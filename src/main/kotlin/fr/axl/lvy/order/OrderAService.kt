package fr.axl.lvy.order

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
) {
  companion object {
    private const val ORDER_NUMBER_PREFIX = "CoD_PO_"
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
    if (!allowed.contains(newStatus)) {
      throw IllegalStateException("Cannot transition from ${order.status} to $newStatus")
    }
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
      newLine.product = line.product
      newLine.description = line.description
      newLine.hsCode = line.hsCode
      newLine.madeIn = line.madeIn
      newLine.clientProductCode = line.clientProductCode
      newLine.quantity = line.quantity
      newLine.unit = line.unit
      newLine.unitPriceExclTax = line.unitPriceExclTax
      newLine.discountPercent = line.discountPercent
      newLine.vatRate = line.vatRate
      newLine.position = line.position
      newLine.recalculate()
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
      val product = line.product!!
      val newLine = DocumentLine(DocumentLine.DocumentType.ORDER_B, orderB.id!!, line.designation)
      newLine.product = product
      newLine.description = line.description
      newLine.hsCode = line.hsCode
      newLine.madeIn = line.madeIn
      newLine.clientProductCode = line.clientProductCode
      newLine.quantity = line.quantity
      newLine.unit = line.unit
      newLine.unitPriceExclTax = product.purchasePriceExclTax
      newLine.discountPercent = line.discountPercent
      newLine.vatRate = line.vatRate
      newLine.position = line.position
      newLine.recalculate()
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

  @Transactional(readOnly = true) fun nextOrderNumber(): String = generateNextOrderNumber()

  private fun getAllowedTransitions(current: OrderA.OrderAStatus): Set<OrderA.OrderAStatus> =
    when (current) {
      OrderA.OrderAStatus.CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED
      OrderA.OrderAStatus.IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION
      OrderA.OrderAStatus.READY -> ALLOWED_TRANSITIONS_FROM_READY
      OrderA.OrderAStatus.DELIVERED -> ALLOWED_TRANSITIONS_FROM_DELIVERED
      else -> emptySet()
    }

  private fun generateNextOrderNumber(): String {
    val nextNumber =
      orderARepository
        .findAllOrderNumbers()
        .mapNotNull { orderNumber -> orderNumber.removePrefix(ORDER_NUMBER_PREFIX).toIntOrNull() }
        .maxOrNull()
        ?.plus(1) ?: 1
    return ORDER_NUMBER_PREFIX + nextNumber.toString().padStart(3, '0')
  }
}
