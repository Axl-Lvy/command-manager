package fr.axl.lvy.order

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.sale.SalesNetstoneService
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Codig customer orders. Handles the status state machine, order duplication,
 * MTO supplier order generation, and synchronization with the originating sale's Netstone
 * counterpart.
 */
@Service
class OrderCodigService(
  private val orderCodigRepository: OrderCodigRepository,
  private val orderNetstoneRepository: OrderNetstoneRepository,
  private val documentLineRepository: DocumentLineRepository,
  private val documentLineService: DocumentLineService,
  private val salesCodigRepository: SalesCodigRepository,
  private val salesNetstoneService: SalesNetstoneService,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
) {
  // Pre-registered so they appear in Prometheus at startup with value 0.
  private val ordersCreatedCounter = meterRegistry.counter("order.codig")
  private val ordersDuplicatedCounter = meterRegistry.counter("order.codig.duplicated")
  private val mtoTriggeredCounter = meterRegistry.counter("order.codig.mto.triggered")

  companion object {
    private val log = LoggerFactory.getLogger(OrderCodigService::class.java)
    private val ALLOWED_TRANSITIONS_FROM_DRAFT =
      setOf(OrderCodig.OrderCodigStatus.CONFIRMED, OrderCodig.OrderCodigStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      setOf(OrderCodig.OrderCodigStatus.DELIVERED, OrderCodig.OrderCodigStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_DELIVERED = setOf(OrderCodig.OrderCodigStatus.INVOICED)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<OrderCodig> = orderCodigRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<OrderCodig> = orderCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<OrderCodig> =
    Optional.ofNullable(orderCodigRepository.findDetailedById(id))

  @Transactional
  fun save(order: OrderCodig): OrderCodig {
    val isNew = order.orderNumber.isBlank()
    if (isNew) {
      order.orderNumber = generateNextOrderNumber()
    }
    if (order.billingAddress.isNullOrBlank()) {
      order.billingAddress = order.client.billingAddress
    }
    if (order.shippingAddress.isNullOrBlank()) {
      order.shippingAddress = order.client.shippingAddress
    }
    if (order.deliveryLocation.isNullOrBlank()) {
      order.deliveryLocation = order.client.deliveryPort
    }
    val saved = orderCodigRepository.save(order)
    if (isNew) {
      ordersCreatedCounter.increment()
      log.info("OrderCodig created: number={} clientId={}", saved.orderNumber, saved.client.id)
    }
    return saved
  }

  @Transactional
  fun delete(id: Long) {
    orderCodigRepository.findById(id).ifPresent { it.softDelete() }
  }

  /**
   * Transitions the order to [newStatus] following the allowed state machine. On CONFIRMED:
   * creates/updates a Netstone sale if MTO products are present. On CANCELLED: cleans up the linked
   * Netstone sale.
   */
  @Transactional
  fun changeStatus(order: OrderCodig, newStatus: OrderCodig.OrderCodigStatus): OrderCodig {
    val allowed = getAllowedTransitions(order.status)
    require(allowed.contains(newStatus)) { "Cannot transition from ${order.status} to $newStatus" }
    val fromStatus = order.status
    order.status = newStatus
    val saved = orderCodigRepository.save(order)
    val savedId = saved.id ?: return saved

    MDC.put("orderId", savedId.toString())
    MDC.put("orderNumber", saved.orderNumber)
    MDC.put("fromStatus", fromStatus.name)
    MDC.put("toStatus", newStatus.name)
    try {
      meterRegistry
        .counter("order.codig.status.transition", "from", fromStatus.name, "to", newStatus.name)
        .increment()
      log.info("OrderCodig {} transitioned {} -> {}", saved.orderNumber, fromStatus, newStatus)

      val originatingSale = salesCodigRepository.findByOrderCodigId(savedId)
      if (originatingSale != null) {
        val saleId = originatingSale.id ?: return saved
        if (newStatus == OrderCodig.OrderCodigStatus.CONFIRMED) {
          val lines = findLines(savedId)
          if (lines.any { it.product?.isMtoProduct() == true }) {
            salesNetstoneService.createOrUpdateFromSalesCodig(
              originatingSale,
              saved.orderDate,
              saved.expectedDeliveryDate,
              saved.notes,
              lines,
            )
          } else {
            salesNetstoneService.deleteBySalesCodigId(saleId)
          }
        } else if (newStatus == OrderCodig.OrderCodigStatus.CANCELLED) {
          salesNetstoneService.deleteBySalesCodigId(saleId)
        }
      }
    } finally {
      MDC.remove("orderId")
      MDC.remove("orderNumber")
      MDC.remove("fromStatus")
      MDC.remove("toStatus")
    }
    return saved
  }

  /**
   * Creates a deep copy of [source] (including all line items) with a new order number and DRAFT
   * status.
   */
  @Transactional
  fun duplicate(source: OrderCodig, newOrderNumber: String): OrderCodig {
    var copy = OrderCodig(newOrderNumber, source.client, source.orderDate)
    copy.clientReference = source.clientReference
    copy.subject = source.subject
    copy.billingAddress = source.billingAddress
    copy.shippingAddress = source.shippingAddress
    copy.vatRate = source.vatRate
    copy.currency = source.currency
    copy.exchangeRate = source.exchangeRate
    copy.purchasePriceExclTax = source.purchasePriceExclTax
    copy.incoterms = source.incoterms
    copy.incotermLocation = source.incotermLocation
    copy.deliveryLocation = source.deliveryLocation
    copy.notes = source.notes
    copy.conditions = source.conditions
    copy.sourceOrder = source
    copy = orderCodigRepository.save(copy)

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        source.id!!,
      )
    for (line in lines) {
      val newLine = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, copy.id!!, line.designation)
      newLine.copyFieldsFrom(line)
      newLine.position = line.position
      documentLineRepository.save(newLine)
    }

    val copyLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        copy.id!!,
      )
    copy.recalculateTotals(copyLines)
    val result = orderCodigRepository.save(copy)

    ordersDuplicatedCounter.increment()
    log.info("OrderCodig duplicated: source={} copy={}", source.orderNumber, result.orderNumber)
    return result
  }

  /**
   * Creates a Netstone supplier order for all MTO (made-to-order) line items. Lines are copied with
   * their purchase price instead of selling price.
   */
  @Transactional
  fun handleMto(order: OrderCodig, orderNetstoneNumber: String) {
    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        order.id!!,
      )
    val mtoLines = lines.filter { it.product?.isMtoProduct() == true }

    if (mtoLines.isEmpty()) return

    var orderNetstone = OrderNetstone(orderNetstoneNumber, order)
    orderNetstone = orderNetstoneRepository.save(orderNetstone)

    for (line in mtoLines) {
      val newLine =
        DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, orderNetstone.id!!, line.designation)
      newLine.copyFieldsFrom(line, overrideUnitPrice = line.product!!.purchasePriceExclTax)
      newLine.position = line.position
      documentLineRepository.save(newLine)
    }

    val orderNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        orderNetstone.id!!,
      )
    orderNetstone.recalculateTotals(orderNetstoneLines)
    orderNetstone = orderNetstoneRepository.save(orderNetstone)

    order.orderNetstone = orderNetstone
    orderCodigRepository.save(order)

    mtoTriggeredCounter.increment()
    log.info(
      "MTO supplier order triggered: codigOrder={} netstoneOrder={} mtoLines={}",
      order.orderNumber,
      orderNetstoneNumber,
      mtoLines.size,
    )
  }

  @Transactional(readOnly = true)
  fun findLines(orderId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.ORDER_CODIG, orderId)

  /**
   * Saves the order and replaces its line items atomically. Recalculates totals and purchase price,
   * and synchronizes the linked Netstone sale if the order is confirmed.
   */
  @Transactional
  fun saveWithLines(order: OrderCodig, lines: List<DocumentLine>): OrderCodig {
    val saved = save(order)

    val persistedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_CODIG,
        saved.id!!,
        lines,
        overrideVatRate = saved.vatRate,
      )

    saved.recalculateTotals(persistedLines)
    saved.purchasePriceExclTax =
      persistedLines.fold(BigDecimal.ZERO) { acc, line ->
        acc.add((line.product?.purchasePriceExclTax ?: BigDecimal.ZERO).multiply(line.quantity))
      }
    val persistedOrder = orderCodigRepository.save(saved)
    val orderId = persistedOrder.id ?: return persistedOrder
    val originatingSale = salesCodigRepository.findByOrderCodigId(orderId)
    if (persistedOrder.status == OrderCodig.OrderCodigStatus.CONFIRMED && originatingSale != null) {
      val saleId = originatingSale.id ?: return persistedOrder
      if (persistedLines.any { it.product?.isMtoProduct() == true }) {
        salesNetstoneService.createOrUpdateFromSalesCodig(
          originatingSale,
          persistedOrder.orderDate,
          persistedOrder.expectedDeliveryDate,
          persistedOrder.notes,
          persistedLines,
        )
      } else {
        salesNetstoneService.deleteBySalesCodigId(saleId)
      }
    }
    return persistedOrder
  }

  private fun getAllowedTransitions(
    current: OrderCodig.OrderCodigStatus
  ): Set<OrderCodig.OrderCodigStatus> =
    when (current) {
      OrderCodig.OrderCodigStatus.DRAFT -> ALLOWED_TRANSITIONS_FROM_DRAFT
      OrderCodig.OrderCodigStatus.CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED
      OrderCodig.OrderCodigStatus.DELIVERED -> ALLOWED_TRANSITIONS_FROM_DELIVERED
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.ORDER_CODIG)
}
