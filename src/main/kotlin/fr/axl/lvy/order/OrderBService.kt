package fr.axl.lvy.order

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import java.time.LocalDate
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderBService(
  private val orderBRepository: OrderBRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  companion object {
    private val ALLOWED_TRANSITIONS_FROM_SENT =
      setOf(OrderB.OrderBStatus.CONFIRMED, OrderB.OrderBStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      setOf(OrderB.OrderBStatus.IN_PRODUCTION, OrderB.OrderBStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION =
      setOf(OrderB.OrderBStatus.RECEIVED, OrderB.OrderBStatus.CANCELLED)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<OrderB> = orderBRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<OrderB> = orderBRepository.findById(id)

  @Transactional
  fun save(order: OrderB): OrderB {
    if (order.orderNumber.isBlank()) {
      order.orderNumber = generateNextOrderNumber()
    }
    return orderBRepository.save(order)
  }

  @Transactional
  fun delete(id: Long) {
    orderBRepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun changeStatus(order: OrderB, newStatus: OrderB.OrderBStatus): OrderB {
    val allowed = getAllowedTransitions(order.status)
    if (!allowed.contains(newStatus)) {
      throw IllegalStateException("Cannot transition from ${order.status} to $newStatus")
    }
    order.status = newStatus
    return orderBRepository.save(order)
  }

  @Transactional
  fun markReceived(
    order: OrderB,
    receptionDate: LocalDate,
    conforming: Boolean,
    receptionReserve: String,
  ): OrderB {
    order.receptionDate = receptionDate
    order.receptionConforming = conforming
    order.receptionReserve = receptionReserve
    order.status = OrderB.OrderBStatus.RECEIVED
    return orderBRepository.save(order)
  }

  @Transactional(readOnly = true)
  fun findLines(orderId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.ORDER_B, orderId)

  @Transactional
  fun saveWithLines(order: OrderB, lines: List<DocumentLine>): OrderB {
    val saved = save(order)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.ORDER_B, saved.id!!, lines)

    saved.recalculateTotals(persistedLines)
    return orderBRepository.save(saved)
  }

  private fun getAllowedTransitions(current: OrderB.OrderBStatus): Set<OrderB.OrderBStatus> =
    when (current) {
      OrderB.OrderBStatus.SENT -> ALLOWED_TRANSITIONS_FROM_SENT
      OrderB.OrderBStatus.CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED
      OrderB.OrderBStatus.IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.ORDER_B)
}
