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
        ?: SalesB("", salesA).apply { status = SalesB.SalesBStatus.VALIDATED }

    sale.salesA = salesA
    sale.saleDate = salesA.saleDate
    sale.expectedDeliveryDate = salesA.expectedDeliveryDate
    sale.notes = salesA.notes
    sale.status = SalesB.SalesBStatus.VALIDATED

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
    val persistedSale = salesBRepository.save(savedSale)
    syncGeneratedOrder(persistedSale, generatedSaleLines)
    return persistedSale
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

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_B)
}
