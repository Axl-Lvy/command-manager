package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Codig sales. Handles saving with line items, and automatically
 * generates/updates the linked [OrderCodig] and [SalesNetstone] when MTO products are involved.
 */
@Service
class SalesCodigService(
  private val salesCodigRepository: SalesCodigRepository,
  private val orderCodigService: OrderCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
  private val clientService: ClientService,
) {
  private val salesCreatedCounter = meterRegistry.counter("sale.codig")

  companion object {
    private val log = LoggerFactory.getLogger(SalesCodigService::class.java)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<SalesCodig> = salesCodigRepository.findByDeletedAtIsNull()

  /** Paginated fetch for Vaadin lazy-loading grids. */
  @Transactional(readOnly = true)
  fun findAll(pageable: Pageable): Page<SalesCodig> =
    salesCodigRepository.findByDeletedAtIsNull(pageable)

  @Transactional(readOnly = true)
  fun findAllWithLinkedOrder(): List<SalesCodig> =
    salesCodigRepository.findByDeletedAtIsNullAndOrderCodigIsNotNull()

  /** Paginated search of sales that have a generated Codig order. For ComboBox lazy loading. */
  @Transactional(readOnly = true)
  fun searchWithLinkedOrder(filter: String, offset: Int, limit: Int): List<SalesCodig> =
    salesCodigRepository.searchActiveWithLinkedOrder(
      filter,
      PageRequest.of(offset / limit.coerceAtLeast(1), limit),
    )

  @Transactional(readOnly = true)
  fun countSearchWithLinkedOrder(filter: String): Int =
    salesCodigRepository.countActiveWithLinkedOrder(filter).toInt()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesCodig> = salesCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesCodig> =
    Optional.ofNullable(salesCodigRepository.findDetailedById(id))

  /** Returns the sale that generated the given [OrderCodig], if any. */
  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): Optional<SalesCodig> =
    Optional.ofNullable(salesCodigRepository.findByOrderCodigId(orderCodigId))

  @Transactional
  fun save(sale: SalesCodig): SalesCodig {
    val isNew = sale.saleNumber.isBlank()
    if (isNew) {
      sale.saleNumber = generateNextSaleNumber()
    }
    if (sale.billingAddress.isNullOrBlank()) {
      sale.billingAddress = sale.client.billingAddress
    }
    if (sale.shippingAddress.isNullOrBlank()) {
      sale.shippingAddress = sale.client.shippingAddress
    }
    if (sale.paymentTerm == null) {
      sale.paymentTerm = sale.client.paymentTerm
    }
    if (sale.fiscalPosition == null) {
      sale.fiscalPosition = sale.client.fiscalPosition
    }
    val saved = salesCodigRepository.save(sale)
    if (isNew) {
      salesCreatedCounter.increment()
      log.info("SalesCodig created: number={} clientId={}", saved.saleNumber, saved.client.id)
    }
    return saved
  }

  @Transactional
  fun delete(id: Long) {
    salesCodigRepository.findById(id).ifPresent { it.softDelete() }
  }

  /**
   * Generates the auto-created [OrderCodig] from this sale, once. Runs only when the sale has no
   * linked order yet and contains at least one MTO product line. Once the order exists, further
   * sale edits do not alter it — the order becomes an independent document.
   *
   * This one-shot rule (issue #32) prevents routine sale modifications from silently overwriting
   * order fields that a user or the procurement workflow may have since changed.
   */
  @Transactional
  fun syncGeneratedOrder(sale: SalesCodig, saleLines: List<DocumentLine>): SalesCodig {
    if (sale.orderCodig != null) {
      // Order already generated: automation disabled, do not mutate the linked order.
      return sale
    }

    if (saleLines.none { it.product?.isMtoProduct() == true }) {
      // No MTO line, nothing to generate.
      return sale
    }

    val supplier =
      clientService.findDefaultCodigSupplier().orElseThrow {
        IllegalStateException(
          "Aucun fournisseur Netstone par defaut n'est configure dans les societes internes"
        )
      }
    val order = OrderCodig("", supplier, sale.saleDate)
    val codigCompany = clientService.findDefaultCodigCompany().orElse(null)

    order.client = supplier
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.clientReference = sale.clientReference
    order.subject = sale.subject
    order.currency = sale.currency
    order.exchangeRate = sale.exchangeRate
    order.purchasePriceExclTax = sale.purchasePriceExclTax
    order.incoterms = codigCompany?.incoterm?.name
    order.incotermLocation = sale.client.deliveryPort
    order.paymentTerm = supplier.paymentTerm
    order.fiscalPosition = codigCompany?.fiscalPosition
    order.deliveryLocation = sale.client.deliveryPort
    order.billingAddress = sale.billingAddress
    order.shippingAddress = sale.shippingAddress
    order.notes = sale.notes
    order.conditions = sale.conditions
    order.status = OrderCodig.OrderCodigStatus.DRAFT

    val savedOrder = orderCodigService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_CODIG,
        savedOrder.id!!,
        saleLines,
        overrideUnitPrice = { it.product?.purchasePriceExclTax },
      )

    savedOrder.recalculateTotals(generatedLines)
    orderCodigService.save(savedOrder)

    sale.orderCodig = savedOrder

    MDC.put("saleNumber", sale.saleNumber)
    MDC.put("orderNumber", savedOrder.orderNumber)
    try {
      log.info("SalesCodig {} generated OrderCodig {}", sale.saleNumber, savedOrder.orderNumber)
    } finally {
      MDC.remove("saleNumber")
      MDC.remove("orderNumber")
    }

    return salesCodigRepository.save(sale)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_CODIG, saleId)

  /**
   * Saves the sale with its line items, recalculates totals and purchase price, then syncs the
   * generated order.
   */
  @Transactional
  fun saveWithLines(sale: SalesCodig, lines: List<DocumentLine>): SalesCodig {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.SALES_CODIG, saved.id!!, lines)

    saved.purchasePriceExclTax =
      persistedLines.fold(BigDecimal.ZERO) { acc, line ->
        acc.add((line.product?.purchasePriceExclTax ?: BigDecimal.ZERO).multiply(line.quantity))
      }
    saved.recalculateTotals(persistedLines)
    return syncGeneratedOrder(saved, persistedLines)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_CODIG)
}
