package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBService
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SalesBService(
  private val salesBRepository: SalesBRepository,
  private val orderBService: OrderBService,
  private val documentLineRepository: DocumentLineRepository,
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
  fun createOrUpdateFromValidatedSalesA(salesA: SalesA, saleLines: List<DocumentLine>): SalesB {
    val sale =
      salesBRepository.findBySalesAId(salesA.id!!)
        ?: SalesB("", salesA).apply { status = SalesB.SalesBStatus.DRAFT }

    sale.salesA = salesA
    sale.saleDate = salesA.saleDate
    sale.expectedDeliveryDate = salesA.expectedDeliveryDate
    sale.notes = salesA.notes
    if (sale.status == SalesB.SalesBStatus.CANCELLED) {
      sale.status = SalesB.SalesBStatus.DRAFT
    }
    sale.purchasePriceExclTax =
      saleLines.fold(java.math.BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }

    val savedSale = save(sale)

    val existingSaleLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        savedSale.id!!,
      )
    documentLineRepository.deleteAll(existingSaleLines)

    val generatedSaleLines =
      saleLines
        .filter { it.product?.mto == true }
        .mapIndexed { index, line ->
          DocumentLine(DocumentLine.DocumentType.SALES_B, savedSale.id!!, line.designation).apply {
            copyFieldsFrom(line)
            position = index
          }
        }
    documentLineRepository.saveAll(generatedSaleLines)

    savedSale.recalculateTotals(generatedSaleLines)
    return salesBRepository.save(savedSale)
  }

  @Transactional
  fun createOrUpdateFromConfirmedOrderA(
    salesA: SalesA,
    orderA: fr.axl.lvy.order.OrderA,
    orderLines: List<DocumentLine>,
  ): SalesB {
    val sale =
      salesBRepository.findBySalesAId(salesA.id!!)
        ?: SalesB("", salesA).apply { status = SalesB.SalesBStatus.DRAFT }

    sale.salesA = salesA
    sale.saleDate = orderA.orderDate
    sale.expectedDeliveryDate = orderA.expectedDeliveryDate
    sale.notes = orderA.notes
    if (sale.status == SalesB.SalesBStatus.CANCELLED) {
      sale.status = SalesB.SalesBStatus.DRAFT
    }
    sale.purchasePriceExclTax =
      orderLines.fold(java.math.BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }

    val savedSale = save(sale)
    val existingSaleLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        savedSale.id!!,
      )
    documentLineRepository.deleteAll(existingSaleLines)

    val generatedSaleLines =
      orderLines
        .filter { it.product?.mto == true }
        .mapIndexed { index, line ->
          DocumentLine(DocumentLine.DocumentType.SALES_B, savedSale.id!!, line.designation).apply {
            copyFieldsFrom(line)
            position = index
          }
        }
    documentLineRepository.saveAll(generatedSaleLines)

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
    val existingOrderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_B,
        savedOrder.id!!,
      )
    documentLineRepository.deleteAll(existingOrderLines)

    val generatedLines =
      saleLines.mapIndexed { index, line ->
        DocumentLine(DocumentLine.DocumentType.ORDER_B, savedOrder.id!!, line.designation).apply {
          copyFieldsFrom(line)
          position = index
        }
      }
    documentLineRepository.saveAll(generatedLines)

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
    documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
      DocumentLine.DocumentType.SALES_B,
      saleId,
    )

  @Transactional
  fun saveWithLines(sale: SalesB, lines: List<DocumentLine>): SalesB {
    val saved = save(sale)

    val existingLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        saved.id!!,
      )
    documentLineRepository.deleteAll(existingLines)

    val persistedLines =
      lines.mapIndexed { i, line ->
        DocumentLine(DocumentLine.DocumentType.SALES_B, saved.id!!, line.designation).apply {
          copyFieldsFrom(line)
          position = i
        }
      }
    documentLineRepository.saveAll(persistedLines)

    saved.recalculateTotals(persistedLines)
    if (saved.status == SalesB.SalesBStatus.VALIDATED) {
      return syncGeneratedOrder(saved, persistedLines)
    }

    saved.orderB = null
    return salesBRepository.save(saved)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_B)
}
