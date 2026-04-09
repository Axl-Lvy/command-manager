package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import java.math.BigDecimal
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SalesCodigService(
  private val salesCodigRepository: SalesCodigRepository,
  private val orderCodigService: OrderCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesCodig> = salesCodigRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesCodig> = salesCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesCodig> =
    Optional.ofNullable(salesCodigRepository.findDetailedById(id))

  @Transactional
  fun save(sale: SalesCodig): SalesCodig {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    if (sale.billingAddress.isNullOrBlank()) {
      sale.billingAddress = sale.client.billingAddress
    }
    if (sale.shippingAddress.isNullOrBlank()) {
      sale.shippingAddress = sale.client.shippingAddress
    }
    return salesCodigRepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesCodigRepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun syncGeneratedOrder(sale: SalesCodig, saleLines: List<DocumentLine>): SalesCodig {
    if (saleLines.none { it.product?.isMtoProduct() == true }) {
      sale.orderCodig?.id?.let { orderCodigService.delete(it) }
      sale.id?.let { salesNetstoneService.deleteBySalesCodigId(it) }
      sale.orderCodig = null
      return salesCodigRepository.save(sale)
    }

    val order = sale.orderCodig ?: OrderCodig("", sale.client, sale.saleDate)

    order.client = sale.client
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.clientReference = sale.clientReference
    order.subject = sale.subject
    order.currency = sale.currency
    order.exchangeRate = sale.exchangeRate
    order.purchasePriceExclTax = sale.purchasePriceExclTax
    order.incoterms = sale.incoterms
    order.billingAddress = sale.billingAddress
    order.shippingAddress = sale.shippingAddress
    order.notes = sale.notes
    order.conditions = sale.conditions
    if (sale.orderCodig == null) {
      order.status = OrderCodig.OrderCodigStatus.DRAFT
    }

    val savedOrder = orderCodigService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_CODIG,
        savedOrder.id!!,
        saleLines,
      )

    savedOrder.recalculateTotals(generatedLines)
    orderCodigService.save(savedOrder)

    sale.orderCodig = savedOrder
    return salesCodigRepository.save(sale)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_CODIG, saleId)

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
