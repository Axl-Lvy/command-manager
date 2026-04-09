package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import java.math.BigDecimal
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SalesAService(
  private val salesARepository: SalesARepository,
  private val orderAService: OrderAService,
  private val salesBService: SalesBService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesA> = salesARepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesA> = salesARepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesA> =
    Optional.ofNullable(salesARepository.findDetailedById(id))

  @Transactional
  fun save(sale: SalesA): SalesA {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    if (sale.billingAddress.isNullOrBlank()) {
      sale.billingAddress = sale.client.billingAddress
    }
    if (sale.shippingAddress.isNullOrBlank()) {
      sale.shippingAddress = sale.client.shippingAddress
    }
    return salesARepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesARepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun syncGeneratedOrder(sale: SalesA, saleLines: List<DocumentLine>): SalesA {
    if (saleLines.none { it.product?.isMtoProduct() == true }) {
      sale.orderA?.id?.let { orderAService.delete(it) }
      sale.id?.let { salesBService.deleteBySalesAId(it) }
      sale.orderA = null
      return salesARepository.save(sale)
    }

    val order = sale.orderA ?: OrderA("", sale.client, sale.saleDate)

    order.client = sale.client
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.clientReference = sale.clientReference
    order.subject = sale.subject
    order.currency = sale.currency
    order.exchangeRate = sale.exchangeRate
    order.purchasePriceExclTax = sale.purchasePriceExclTax
    order.vatRate = sale.vatRate
    order.incoterms = sale.incoterms
    order.billingAddress = sale.billingAddress
    order.shippingAddress = sale.shippingAddress
    order.notes = sale.notes
    order.conditions = sale.conditions
    if (sale.orderA == null) {
      order.status = OrderA.OrderAStatus.DRAFT
    }

    val savedOrder = orderAService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_A,
        savedOrder.id!!,
        saleLines,
        overrideVatRate = sale.vatRate,
      )

    savedOrder.recalculateTotals(generatedLines)
    orderAService.save(savedOrder)

    sale.orderA = savedOrder
    return salesARepository.save(sale)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_A, saleId)

  @Transactional
  fun saveWithLines(sale: SalesA, lines: List<DocumentLine>): SalesA {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.SALES_A,
        saved.id!!,
        lines,
        overrideVatRate = saved.vatRate,
      )

    saved.purchasePriceExclTax =
      persistedLines.fold(BigDecimal.ZERO) { acc, line ->
        acc.add((line.product?.purchasePriceExclTax ?: BigDecimal.ZERO).multiply(line.quantity))
      }
    saved.recalculateTotals(persistedLines)
    return syncGeneratedOrder(saved, persistedLines)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_A)
}
