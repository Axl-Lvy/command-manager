package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.ProductService
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Netstone sales. Handles creation from a Codig sale's MTO lines, and
 * generates/syncs the linked [OrderNetstone] when the sale is validated.
 */
@Service
class SalesNetstoneService(
  private val salesNetstoneRepository: SalesNetstoneRepository,
  private val orderNetstoneService: OrderNetstoneService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val meterRegistry: MeterRegistry,
) {
  private val salesCreatedCounter = meterRegistry.counter("sale.netstone")

  companion object {
    private val log = LoggerFactory.getLogger(SalesNetstoneService::class.java)
    private const val ERR_ORDER_CODIG_MISSING = ERR_ORDER_CODIG_MISSING
  }

  @Transactional(readOnly = true)
  fun findAll(): List<SalesNetstone> = salesNetstoneRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesNetstone> = salesNetstoneRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesNetstone> =
    Optional.ofNullable(salesNetstoneRepository.findDetailedById(id))

  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): Optional<SalesNetstone> =
    Optional.ofNullable(salesNetstoneRepository.findByOrderCodigId(orderCodigId))

  @Transactional
  fun save(sale: SalesNetstone): SalesNetstone {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    val orderCodig =
      sale.salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    if (sale.incotermLocation.isNullOrBlank()) {
      sale.incotermLocation = orderCodig.incotermLocation
    }
    if (sale.fiscalPosition == null) {
      sale.fiscalPosition =
        clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
    }
    if (sale.currency.isBlank()) {
      sale.currency = orderCodig.currency
    }
    return salesNetstoneRepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesNetstoneRepository.findById(id).ifPresent { it.softDelete() }
  }

  /**
   * Creates or updates a Netstone sale from a Codig sale's MTO line items. Only MTO product lines
   * are copied. Re-activates a previously cancelled sale if needed.
   */
  @Transactional
  fun createOrUpdateFromSalesCodig(
    salesCodig: SalesCodig,
    saleDate: LocalDate,
    expectedDeliveryDate: LocalDate?,
    notes: String?,
    sourceLines: List<DocumentLine>,
  ): SalesNetstone {
    val sourceOrderCodig =
      salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    val existing = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    val isNew = existing == null
    val sale = existing ?: SalesNetstone("", salesCodig).apply { status = SalesStatus.DRAFT }

    sale.salesCodig = salesCodig
    sale.saleDate = saleDate
    sale.expectedDeliveryDate = expectedDeliveryDate
    sale.incoterms = sourceOrderCodig.incoterms
    sale.incotermLocation = sourceOrderCodig.incotermLocation
    sale.shippingAddress = salesCodig.shippingAddress
    sale.notes = notes
    sale.fiscalPosition =
      clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
    sale.currency = sourceOrderCodig.currency
    sale.exchangeRate = sourceOrderCodig.exchangeRate
    if (sale.status == SalesStatus.CANCELLED) {
      sale.status = SalesStatus.DRAFT
    }
    sale.purchasePriceExclTax =
      sourceLines.fold(BigDecimal.ZERO) { acc, line ->
        acc.add((line.product?.purchasePriceExclTax ?: BigDecimal.ZERO).multiply(line.quantity))
      }

    val savedSale = save(sale)

    val generatedSaleLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSale.id!!,
        sourceLines,
        overrideDiscountPercent = BigDecimal.ZERO,
        filter = { it.product?.isMtoProduct() == true },
      )

    savedSale.recalculateTotals(generatedSaleLines)
    val result = salesNetstoneRepository.save(savedSale)

    if (isNew) {
      salesCreatedCounter.increment()
    }
    MDC.put("saleNetstoneNumber", result.saleNumber)
    MDC.put("salesCodigNumber", salesCodig.saleNumber)
    try {
      if (isNew) {
        log.info(
          "SalesNetstone {} created from SalesCodig {}",
          result.saleNumber,
          salesCodig.saleNumber,
        )
      } else {
        log.info(
          "SalesNetstone {} synced from SalesCodig {}",
          result.saleNumber,
          salesCodig.saleNumber,
        )
      }
    } finally {
      MDC.remove("saleNetstoneNumber")
      MDC.remove("salesCodigNumber")
    }
    return result
  }

  /**
   * Synchronizes the auto-generated [OrderNetstone] from this Netstone sale. Requires that the
   * parent Codig sale already has a generated [OrderCodig].
   */
  @Transactional
  fun syncGeneratedOrder(sale: SalesNetstone, saleLines: List<DocumentLine>): SalesNetstone {
    val sourceOrderCodig =
      sale.salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    val order = sale.orderNetstone ?: OrderNetstone("", sourceOrderCodig)

    order.orderCodig = sourceOrderCodig
    order.supplier =
      saleLines.firstNotNullOfOrNull { line ->
        line.product
          ?.id
          ?.let { productService.findDetailedById(it).orElse(null) }
          ?.suppliers
          ?.firstOrNull()
      }
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.paymentTerm = clientService.findDefaultCodigSupplier().map { it.paymentTerm }.orElse(null)
    order.fiscalPosition =
      clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
    order.incoterms = sale.incoterms
    order.incotermLocation = sale.incotermLocation
    order.notes = sale.notes
    order.purchasePriceExclTax = sale.purchasePriceExclTax

    val savedOrder = orderNetstoneService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        savedOrder.id!!,
        saleLines,
      )

    savedOrder.recalculateTotals(generatedLines)
    orderNetstoneService.save(savedOrder)

    sale.orderNetstone = savedOrder
    return salesNetstoneRepository.save(sale)
  }

  /** Soft-deletes the Netstone sale (and its generated order) linked to the given Codig sale. */
  @Transactional
  fun deleteBySalesCodigId(salesCodigId: Long) {
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodigId) ?: return
    salesNetstone.orderNetstone?.id?.let { orderNetstoneService.delete(it) }
    salesNetstone.softDelete()
    salesNetstoneRepository.save(salesNetstone)
    log.info(
      "SalesNetstone {} soft-deleted (salesCodigId={})",
      salesNetstone.saleNumber,
      salesCodigId,
    )
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_NETSTONE, saleId)

  /**
   * Saves the sale with its lines. If validated, syncs the generated order; otherwise cleans up any
   * existing order.
   */
  @Transactional
  fun saveWithLines(sale: SalesNetstone, lines: List<DocumentLine>): SalesNetstone {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.SALES_NETSTONE, saved.id!!, lines)

    saved.recalculateTotals(persistedLines)
    if (
      saved.status == SalesStatus.VALIDATED &&
        persistedLines.any { it.product?.isMtoProduct() == true }
    ) {
      return syncGeneratedOrder(saved, persistedLines)
    }

    saved.orderNetstone?.id?.let { orderNetstoneService.delete(it) }
    saved.orderNetstone = null
    return salesNetstoneRepository.save(saved)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_NETSTONE)
}
