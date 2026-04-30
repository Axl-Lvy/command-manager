package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.ProductPriceCompany
import fr.axl.lvy.product.ProductService
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private const val ERR_ORDER_CODIG_MISSING = "Sales Codig must generate Order Codig first"
  }

  @Transactional(readOnly = true)
  fun findAll(): List<SalesNetstone> = salesNetstoneRepository.findByDeletedAtIsNull()

  /** Paginated fetch for Vaadin lazy-loading grids. */
  @Transactional(readOnly = true)
  fun findAll(pageable: Pageable): Page<SalesNetstone> =
    salesNetstoneRepository.findByDeletedAtIsNull(pageable)

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesNetstone> = salesNetstoneRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesNetstone> =
    Optional.ofNullable(salesNetstoneRepository.findDetailedById(id))

  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): Optional<SalesNetstone> =
    Optional.ofNullable(salesNetstoneRepository.findByOrderCodigId(orderCodigId))

  @Transactional(readOnly = true)
  fun findBySalesCodigId(salesCodigId: Long): Optional<SalesNetstone> =
    Optional.ofNullable(salesNetstoneRepository.findBySalesCodigId(salesCodigId))

  @Transactional
  fun save(sale: SalesNetstone): SalesNetstone {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    val orderCodig = sale.salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    if (sale.incotermLocation.isNullOrBlank()) {
      sale.incotermLocation = orderCodig.incotermLocation
    }
    if (sale.fiscalPosition == null) {
      sale.fiscalPosition =
        clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
    }
    if (sale.paymentTerm == null) {
      sale.paymentTerm = orderCodig.paymentTerm
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
   * are copied. Once the Netstone sale has progressed past DRAFT it is independent: further
   * upstream saves no longer mutate its fields or lines.
   */
  @Transactional
  fun createOrUpdateFromSalesCodig(
    salesCodig: SalesCodig,
    saleDate: LocalDate,
    expectedDeliveryDate: LocalDate?,
    notes: String?,
    sourceLines: List<DocumentLine>,
  ): SalesNetstone {
    val sourceOrderCodig = salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    val existing = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    if (existing != null && existing.status != SalesStatus.DRAFT) {
      return existing
    }
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
    sale.paymentTerm = sourceOrderCodig.paymentTerm
    sale.currency = sourceOrderCodig.currency
    sale.exchangeRate = sourceOrderCodig.exchangeRate
    val savedSale = save(sale)

    val generatedSaleLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSale.id!!,
        sourceLines,
        overrideUnitPrice = { line ->
          productService
            .findPurchasePrice(line.product?.id, ProductPriceCompany.NETSTONE)
            ?.priceExclTax
        },
        overrideDiscountPercent = BigDecimal.ZERO,
        filter = { it.product?.isMtoProduct() == true },
      )

    firstPurchaseCurrency(generatedSaleLines)?.let { savedSale.currency = it }
    savedSale.purchasePriceExclTax = computeLineTotal(generatedSaleLines)
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
   * parent Codig sale already has a generated [OrderCodig]. Once the Netstone order has progressed
   * past SENT it becomes independent: further upstream saves no longer mutate it.
   */
  @Transactional
  fun syncGeneratedOrder(sale: SalesNetstone, saleLines: List<DocumentLine>): SalesNetstone {
    val existingOrder = sale.orderNetstone
    if (existingOrder != null && existingOrder.status != OrderNetstone.OrderNetstoneStatus.SENT) {
      return sale
    }
    val sourceOrderCodig = sale.salesCodig.orderCodig ?: error(ERR_ORDER_CODIG_MISSING)
    val order = existingOrder ?: OrderNetstone("", sourceOrderCodig)

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
    order.paymentTerm = sale.paymentTerm
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
   * existing order — but only while that order is still in its initial SENT status, to avoid wiping
   * an order the user has already acted upon.
   */
  @Transactional
  fun saveWithLines(sale: SalesNetstone, lines: List<DocumentLine>): SalesNetstone {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.SALES_NETSTONE, saved.id!!, lines)

    saved.purchasePriceExclTax = computeLineTotal(persistedLines)
    saved.recalculateTotals(persistedLines)
    if (
      saved.status == SalesStatus.VALIDATED &&
        persistedLines.any { it.product?.isMtoProduct() == true }
    ) {
      return syncGeneratedOrder(saved, persistedLines)
    }

    saved.orderNetstone?.let { order ->
      if (order.status == OrderNetstone.OrderNetstoneStatus.SENT) {
        order.id?.let { orderNetstoneService.delete(it) }
        saved.orderNetstone = null
      }
    }
    return salesNetstoneRepository.save(saved)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_NETSTONE)

  private fun computeLineTotal(lines: List<DocumentLine>): BigDecimal =
    lines.fold(BigDecimal.ZERO) { acc, line ->
      acc.add(line.unitPriceExclTax.multiply(line.quantity))
    }

  private fun firstPurchaseCurrency(lines: List<DocumentLine>): String? =
    lines.firstNotNullOfOrNull { line ->
      productService.findPurchasePrice(line.product?.id, ProductPriceCompany.NETSTONE)?.currency
    }
}
