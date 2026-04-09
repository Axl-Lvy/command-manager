package fr.axl.lvy.order

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import java.time.LocalDate
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderNetstoneService(
  private val orderNetstoneRepository: OrderNetstoneRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  companion object {
    private val ALLOWED_TRANSITIONS_FROM_SENT =
      setOf(
        OrderNetstone.OrderNetstoneStatus.CONFIRMED,
        OrderNetstone.OrderNetstoneStatus.CANCELLED,
      )
    private val ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      setOf(
        OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION,
        OrderNetstone.OrderNetstoneStatus.CANCELLED,
      )
    private val ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION =
      setOf(OrderNetstone.OrderNetstoneStatus.RECEIVED, OrderNetstone.OrderNetstoneStatus.CANCELLED)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<OrderNetstone> = orderNetstoneRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<OrderNetstone> = orderNetstoneRepository.findById(id)

  @Transactional
  fun save(order: OrderNetstone): OrderNetstone {
    if (order.orderNumber.isBlank()) {
      order.orderNumber = generateNextOrderNumber()
    }
    return orderNetstoneRepository.save(order)
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
    order.status = newStatus
    return orderNetstoneRepository.save(order)
  }

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
    return orderNetstoneRepository.save(order)
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
      OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.ORDER_NETSTONE)
}
