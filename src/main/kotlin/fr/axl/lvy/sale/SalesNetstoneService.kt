package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Netstone sales. Handles creation from a Codig sale's MTO lines, and
 * generates/syncs the linked [OrderNetstone] when the sale is validated.
 */
@Service
class SalesNetstoneService(
  private val salesNetstoneRepository: SalesNetstoneRepository,
  private val orderNetstoneService: OrderNetstoneService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesNetstone> = salesNetstoneRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesNetstone> = salesNetstoneRepository.findById(id)

  @Transactional
  fun save(sale: SalesNetstone): SalesNetstone {
    if (sale.saleNumber.isBlank()) {
      sale.saleNumber = generateNextSaleNumber()
    }
    return salesNetstoneRepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesNetstoneRepository.findById(id).ifPresent { it.softDelete() }
  }

  /**
   * Creates or updates a Netstone sale from a Codig sale's MTO line items. Only MTO product lines
   * are copied. Re-activates a previously cancelled sale if needed.
   */
  @Transactional
  fun createOrUpdateFromSalesCodig(
    salesCodig: SalesCodig,
    saleDate: LocalDate,
    expectedDeliveryDate: LocalDate?,
    notes: String?,
    sourceLines: List<DocumentLine>,
  ): SalesNetstone {
    val sale =
      salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
        ?: SalesNetstone("", salesCodig).apply { status = SalesStatus.DRAFT }

    sale.salesCodig = salesCodig
    sale.saleDate = saleDate
    sale.expectedDeliveryDate = expectedDeliveryDate
    sale.notes = notes
    if (sale.status == SalesStatus.CANCELLED) {
      sale.status = SalesStatus.DRAFT
    }
    sale.purchasePriceExclTax =
      sourceLines.fold(BigDecimal.ZERO) { acc, line ->
        acc.add((line.product?.purchasePriceExclTax ?: BigDecimal.ZERO).multiply(line.quantity))
      }

    val savedSale = save(sale)

    val generatedSaleLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSale.id!!,
        sourceLines,
        filter = { it.product?.isMtoProduct() == true },
      )

    savedSale.recalculateTotals(generatedSaleLines)
    return salesNetstoneRepository.save(savedSale)
  }

  /**
   * Synchronizes the auto-generated [OrderNetstone] from this Netstone sale. Requires that the
   * parent Codig sale already has a generated [OrderCodig].
   */
  @Transactional
  fun syncGeneratedOrder(sale: SalesNetstone, saleLines: List<DocumentLine>): SalesNetstone {
    val sourceOrderCodig =
      sale.salesCodig.orderCodig
        ?: throw IllegalStateException("Sales Codig must generate Order Codig first")
    val order = sale.orderNetstone ?: OrderNetstone("", sourceOrderCodig)

    order.orderCodig = sourceOrderCodig
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.notes = sale.notes
    order.purchasePriceExclTax = sale.purchasePriceExclTax

    val savedOrder = orderNetstoneService.save(order)
    val generatedLines =
      documentLineService.replaceLines(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        savedOrder.id!!,
        saleLines,
      )

    savedOrder.recalculateTotals(generatedLines)
    orderNetstoneService.save(savedOrder)

    sale.orderNetstone = savedOrder
    return salesNetstoneRepository.save(sale)
  }

  /** Soft-deletes the Netstone sale (and its generated order) linked to the given Codig sale. */
  @Transactional
  fun deleteBySalesCodigId(salesCodigId: Long) {
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodigId) ?: return
    salesNetstone.orderNetstone?.id?.let { orderNetstoneService.delete(it) }
    salesNetstone.softDelete()
    salesNetstoneRepository.save(salesNetstone)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_NETSTONE, saleId)

  /**
   * Saves the sale with its lines. If validated, syncs the generated order; otherwise cleans up any
   * existing order.
   */
  @Transactional
  fun saveWithLines(sale: SalesNetstone, lines: List<DocumentLine>): SalesNetstone {
    val saved = save(sale)

    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.SALES_NETSTONE, saved.id!!, lines)

    saved.recalculateTotals(persistedLines)
    if (saved.status == SalesStatus.VALIDATED) {
      return syncGeneratedOrder(saved, persistedLines)
    }

    saved.orderNetstone?.id?.let { orderNetstoneService.delete(it) }
    saved.orderNetstone = null
    return salesNetstoneRepository.save(saved)
  }

  private fun generateNextSaleNumber(): String =
    numberSequenceService.nextNumber(NumberSequenceService.SALES_NETSTONE)
}
