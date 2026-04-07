package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SalesAService(
  private val salesARepository: SalesARepository,
  private val orderAService: OrderAService,
  private val documentLineRepository: DocumentLineRepository,
  private val salesBService: SalesBService,
  private val numberSequenceService: NumberSequenceService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesA> = salesARepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesA> = salesARepository.findById(id)

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
    val order = sale.orderA ?: OrderA("", sale.client, sale.saleDate)

    order.client = sale.client
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.clientReference = sale.clientReference
    order.subject = sale.subject
    order.currency = sale.currency
    order.vatRate = sale.vatRate
    order.incoterms = sale.incoterms
    order.billingAddress = sale.billingAddress
    order.shippingAddress = sale.shippingAddress
    order.notes = sale.notes
    order.conditions = sale.conditions

    val savedOrder = orderAService.save(order)
    val existingOrderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        savedOrder.id!!,
      )
    documentLineRepository.deleteAll(existingOrderLines)

    val generatedLines =
      saleLines.mapIndexed { index, line ->
        DocumentLine(DocumentLine.DocumentType.ORDER_A, savedOrder.id!!, line.designation).apply {
          copyFieldsFrom(line, overrideVatRate = sale.vatRate)
          position = index
        }
      }
    documentLineRepository.saveAll(generatedLines)

    savedOrder.recalculateTotals(generatedLines)
    orderAService.save(savedOrder)

    sale.orderA = savedOrder
    val persistedSale = salesARepository.save(sale)

    val hasMtoLines = saleLines.any { it.product?.mto == true }
    if (persistedSale.status == SalesA.SalesAStatus.VALIDATED && hasMtoLines) {
      salesBService.createOrUpdateFromValidatedSalesA(persistedSale, saleLines)
    } else {
      salesBService.deleteBySalesAId(persistedSale.id!!)
    }

    return persistedSale
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
      DocumentLine.DocumentType.SALES_A,
      saleId,
    )

  @Transactional
  fun saveWithLines(sale: SalesA, lines: List<DocumentLine>): SalesA {
    val saved = save(sale)

    val existingLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_A,
        saved.id!!,
      )
    documentLineRepository.deleteAll(existingLines)

    lines.forEachIndexed { i, line ->
      line.documentType = DocumentLine.DocumentType.SALES_A
      line.documentId = saved.id!!
      line.position = i
      line.vatRate = saved.vatRate
      line.recalculate()
      documentLineRepository.save(line)
    }

    saved.recalculateTotals(lines)
    return syncGeneratedOrder(saved, lines)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_A)
}
