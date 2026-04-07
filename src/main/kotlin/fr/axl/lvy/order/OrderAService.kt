package fr.axl.lvy.order

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderAService(
  private val orderARepository: OrderARepository,
  private val orderBRepository: OrderBRepository,
  private val documentLineRepository: DocumentLineRepository,
  private val numberSequenceService: NumberSequenceService,
) {
  companion object {
    private val ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      setOf(OrderA.OrderAStatus.IN_PRODUCTION, OrderA.OrderAStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION =
      setOf(OrderA.OrderAStatus.READY, OrderA.OrderAStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_READY =
      setOf(OrderA.OrderAStatus.DELIVERED, OrderA.OrderAStatus.CANCELLED)
    private val ALLOWED_TRANSITIONS_FROM_DELIVERED = setOf(OrderA.OrderAStatus.INVOICED)
  }

  @Transactional(readOnly = true)
  fun findAll(): List<OrderA> = orderARepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<OrderA> = orderARepository.findById(id)

  @Transactional
  fun save(order: OrderA): OrderA {
    if (order.orderNumber.isBlank()) {
      order.orderNumber = generateNextOrderNumber()
    }
    if (order.billingAddress.isNullOrBlank()) {
      order.billingAddress = order.client.billingAddress
    }
    if (order.shippingAddress.isNullOrBlank()) {
      order.shippingAddress = order.client.shippingAddress
    }
    return orderARepository.save(order)
  }

  @Transactional
  fun delete(id: Long) {
    orderARepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun changeStatus(order: OrderA, newStatus: OrderA.OrderAStatus): OrderA {
    val allowed = getAllowedTransitions(order.status)
    require(allowed.contains(newStatus)) { "Cannot transition from ${order.status} to $newStatus" }
    order.status = newStatus
    return orderARepository.save(order)
  }

  @Transactional
  fun duplicate(source: OrderA, newOrderNumber: String): OrderA {
    var copy = OrderA(newOrderNumber, source.client, source.orderDate)
    copy.clientReference = source.clientReference
    copy.subject = source.subject
    copy.billingAddress = source.billingAddress
    copy.shippingAddress = source.shippingAddress
    copy.vatRate = source.vatRate
    copy.currency = source.currency
    copy.incoterms = source.incoterms
    copy.notes = source.notes
    copy.conditions = source.conditions
    copy.sourceOrder = source
    copy = orderARepository.save(copy)

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        source.id!!,
      )
    for (line in lines) {
      val newLine = DocumentLine(DocumentLine.DocumentType.ORDER_A, copy.id!!, line.designation)
      newLine.copyFieldsFrom(line)
      newLine.position = line.position
      documentLineRepository.save(newLine)
    }

    val copyLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        copy.id!!,
      )
    copy.recalculateTotals(copyLines)
    return orderARepository.save(copy)
  }

  @Transactional
  fun handleMto(order: OrderA, orderBNumber: String) {
    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        order.id!!,
      )
    val mtoLines = lines.filter { it.product != null && it.product!!.mto }

    if (mtoLines.isEmpty()) return

    var orderB = OrderB(orderBNumber, order)
    orderB = orderBRepository.save(orderB)

    for (line in mtoLines) {
      val newLine = DocumentLine(DocumentLine.DocumentType.ORDER_B, orderB.id!!, line.designation)
      newLine.copyFieldsFrom(line, overrideUnitPrice = line.product!!.purchasePriceExclTax)
      newLine.position = line.position
      documentLineRepository.save(newLine)
    }

    val orderBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_B,
        orderB.id!!,
      )
    orderB.recalculateTotals(orderBLines)
    orderB = orderBRepository.save(orderB)

    order.orderB = orderB
    orderARepository.save(order)
  }

  @Transactional(readOnly = true)
  fun findLines(orderId: Long): List<DocumentLine> =
    documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
      DocumentLine.DocumentType.ORDER_A,
      orderId,
    )

  @Transactional
  fun saveWithLines(order: OrderA, lines: List<DocumentLine>): OrderA {
    val saved = save(order)

    val existingLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        saved.id!!,
      )
    documentLineRepository.deleteAll(existingLines)

    lines.forEachIndexed { i, line ->
      line.documentType = DocumentLine.DocumentType.ORDER_A
      line.documentId = saved.id!!
      line.position = i
      line.vatRate = saved.vatRate
      line.recalculate()
      documentLineRepository.save(line)
    }

    saved.recalculateTotals(lines)
    return orderARepository.save(saved)
  }

  private fun getAllowedTransitions(current: OrderA.OrderAStatus): Set<OrderA.OrderAStatus> =
    when (current) {
      OrderA.OrderAStatus.CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED
      OrderA.OrderAStatus.IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION
      OrderA.OrderAStatus.READY -> ALLOWED_TRANSITIONS_FROM_READY
      OrderA.OrderAStatus.DELIVERED -> ALLOWED_TRANSITIONS_FROM_DELIVERED
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.ORDER_A)
}
