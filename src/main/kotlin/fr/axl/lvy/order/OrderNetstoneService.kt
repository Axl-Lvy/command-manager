package fr.axl.lvy.order

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDate
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Netstone supplier orders. Handles the status state machine and goods
 * reception.
 */
@Service
class OrderNetstoneService(
  private val orderNetstoneRepository: OrderNetstoneRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
) {
  private val ordersCreatedCounter = meterRegistry.counter("order.netstone")

  companion object {
    private val log = LoggerFactory.getLogger(OrderNetstoneService::class.java)
    private val ALLOWED_TRANSITIONS_FROM_SENT =
      setOf(
        OrderNetstone.OrderNetstoneStatus.CONFIRMED,
        OrderNetstone.OrderNetstoneStatus.CANCELLED,
      )
    private val ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      setOf(OrderNetstone.OrderNetstoneStatus.RECEIVED, OrderNetstone.OrderNetstoneStatus.CANCELLED)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<OrderNetstone> = orderNetstoneRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<OrderNetstone> = orderNetstoneRepository.findById(id)

  @Transactional
  fun save(order: OrderNetstone): OrderNetstone {
    val isNew = order.orderNumber.isBlank()
    if (isNew) {
      order.orderNumber = generateNextOrderNumber()
    }
    val saved = orderNetstoneRepository.save(order)
    if (isNew) {
      ordersCreatedCounter.increment()
      log.info("OrderNetstone created: number={}", saved.orderNumber)
    }
    return saved
  }

  @Transactional
  fun delete(id: Long) {
    orderNetstoneRepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun changeStatus(
    order: OrderNetstone,
    newStatus: OrderNetstone.OrderNetstoneStatus,
  ): OrderNetstone {
    val allowed = getAllowedTransitions(order.status)
    check(allowed.contains(newStatus)) { "Cannot transition from ${order.status} to $newStatus" }
    val fromStatus = order.status
    order.status = newStatus
    val saved = orderNetstoneRepository.save(order)

    MDC.put("orderId", saved.id?.toString())
    MDC.put("orderNumber", saved.orderNumber)
    MDC.put("fromStatus", fromStatus.name)
    MDC.put("toStatus", newStatus.name)
    try {
      meterRegistry
        .counter("order.netstone.status.transition", "from", fromStatus.name, "to", newStatus.name)
        .increment()
      log.info("OrderNetstone {} transitioned {} -> {}", saved.orderNumber, fromStatus, newStatus)
    } finally {
      MDC.remove("orderId")
      MDC.remove("orderNumber")
      MDC.remove("fromStatus")
      MDC.remove("toStatus")
    }
    return saved
  }

  /**
   * Records the reception of goods: sets the date, conformity status, and any reserves, then moves
   * to RECEIVED.
   */
  @Transactional
  fun markReceived(
    order: OrderNetstone,
    receptionDate: LocalDate,
    conforming: Boolean,
    receptionReserve: String,
  ): OrderNetstone {
    order.receptionDate = receptionDate
    order.receptionConforming = conforming
    order.receptionReserve = receptionReserve
    order.status = OrderNetstone.OrderNetstoneStatus.RECEIVED
    val saved = orderNetstoneRepository.save(order)

    MDC.put("orderId", saved.id?.toString())
    MDC.put("orderNumber", saved.orderNumber)
    MDC.put("conforming", conforming.toString())
    try {
      log.info(
        "OrderNetstone {} goods received: conforming={} reserve={}",
        saved.orderNumber,
        conforming,
        receptionReserve.ifBlank { "none" },
      )
    } finally {
      MDC.remove("orderId")
      MDC.remove("orderNumber")
      MDC.remove("conforming")
    }
    return saved
  }

  @Transactional(readOnly = true)
  fun findLines(orderId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.ORDER_NETSTONE, orderId)

  @Transactional
  fun saveWithLines(order: OrderNetstone, lines: List<DocumentLine>): OrderNetstone {
    val saved = save(order)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.ORDER_NETSTONE, saved.id!!, lines)

    saved.recalculateTotals(persistedLines)
    return orderNetstoneRepository.save(saved)
  }

  private fun getAllowedTransitions(
    current: OrderNetstone.OrderNetstoneStatus
  ): Set<OrderNetstone.OrderNetstoneStatus> =
    when (current) {
      OrderNetstone.OrderNetstoneStatus.SENT -> ALLOWED_TRANSITIONS_FROM_SENT
      OrderNetstone.OrderNetstoneStatus.CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.ORDER_NETSTONE)
}
