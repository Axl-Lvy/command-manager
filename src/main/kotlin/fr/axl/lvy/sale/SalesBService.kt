package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBService
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SalesBService(
  private val salesBRepository: SalesBRepository,
  private val orderBService: OrderBService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesB> = salesBRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesB> = salesBRepository.findById(id)

  @Transactional
  fun save(sale: SalesB): SalesB {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    return salesBRepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesBRepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun createOrUpdateFromSalesA(
    salesA: SalesA,
    saleDate: LocalDate,
    expectedDeliveryDate: LocalDate?,
    notes: String?,
    sourceLines: List<DocumentLine>,
  ): SalesB {
    val sale =
      salesBRepository.findBySalesAId(salesA.id!!)
        ?: SalesB("", salesA).apply { status = SalesB.SalesBStatus.DRAFT }

    sale.salesA = salesA
    sale.saleDate = saleDate
    sale.expectedDeliveryDate = expectedDeliveryDate
    sale.notes = notes
    if (sale.status == SalesB.SalesBStatus.CANCELLED) {
      sale.status = SalesB.SalesBStatus.DRAFT
    }
    sale.purchasePriceExclTax =
      sourceLines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }

    val savedSale = save(sale)

    val generatedSaleLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.SALES_B,
        savedSale.id!!,
        sourceLines,
        filter = { it.product?.mto == true },
      )

    savedSale.recalculateTotals(generatedSaleLines)
    return salesBRepository.save(savedSale)
  }

  @Transactional
  fun syncGeneratedOrder(sale: SalesB, saleLines: List<DocumentLine>): SalesB {
    val sourceOrderA =
      sale.salesA.orderA ?: throw IllegalStateException("Sales A must generate Order A first")
    val order = sale.orderB ?: OrderB("", sourceOrderA)

    order.orderA = sourceOrderA
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.notes = sale.notes
    order.purchasePriceExclTax = sale.purchasePriceExclTax

    val savedOrder = orderBService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_B,
        savedOrder.id!!,
        saleLines,
      )

    savedOrder.recalculateTotals(generatedLines)
    orderBService.save(savedOrder)

    sale.orderB = savedOrder
    return salesBRepository.save(sale)
  }

  @Transactional
  fun deleteBySalesAId(salesAId: Long) {
    val salesB = salesBRepository.findBySalesAId(salesAId) ?: return
    salesB.orderB?.id?.let { orderBService.delete(it) }
    salesB.softDelete()
    salesBRepository.save(salesB)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_B, saleId)

  @Transactional
  fun saveWithLines(sale: SalesB, lines: List<DocumentLine>): SalesB {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.SALES_B, saved.id!!, lines)

    saved.recalculateTotals(persistedLines)
    if (saved.status == SalesB.SalesBStatus.VALIDATED) {
      return syncGeneratedOrder(saved, persistedLines)
    }

    saved.orderB?.id?.let { orderBService.delete(it) }
    saved.orderB = null
    return salesBRepository.save(saved)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_B)
}
