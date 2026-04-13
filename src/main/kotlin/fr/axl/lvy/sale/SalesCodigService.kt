package fr.axl.lvy.sale

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import java.math.BigDecimal
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business logic for Codig sales. Handles saving with line items, and automatically
 * generates/updates the linked [OrderCodig] and [SalesNetstone] when MTO products are involved.
 */
@Service
class SalesCodigService(
  private val salesCodigRepository: SalesCodigRepository,
  private val orderCodigService: OrderCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val clientService: ClientService,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<SalesCodig> = salesCodigRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findAllWithLinkedOrder(): List<SalesCodig> =
    salesCodigRepository.findByDeletedAtIsNullAndOrderCodigIsNotNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<SalesCodig> = salesCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<SalesCodig> =
    Optional.ofNullable(salesCodigRepository.findDetailedById(id))

  /** Returns the sale that generated the given [OrderCodig], if any. */
  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): Optional<SalesCodig> =
    Optional.ofNullable(salesCodigRepository.findByOrderCodigId(orderCodigId))

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
    if (sale.paymentTerm == null) {
      sale.paymentTerm = sale.client.paymentTerm
    }
    if (sale.fiscalPosition == null) {
      sale.fiscalPosition = sale.client.fiscalPosition
    }
    return salesCodigRepository.save(sale)
  }

  @Transactional
  fun delete(id: Long) {
    salesCodigRepository.findById(id).ifPresent { it.softDelete() }
  }

  /**
   * Synchronizes the auto-generated [OrderCodig] from this sale. If no MTO products remain, the
   * linked order and Netstone sale are deleted. Otherwise, the order is created/updated with the
   * sale's fields and line items.
   */
  @Transactional
  fun syncGeneratedOrder(sale: SalesCodig, saleLines: List<DocumentLine>): SalesCodig {
    if (saleLines.none { it.product?.isMtoProduct() == true }) {
      sale.orderCodig?.id?.let { orderCodigService.delete(it) }
      sale.id?.let { salesNetstoneService.deleteBySalesCodigId(it) }
      sale.orderCodig = null
      return salesCodigRepository.save(sale)
    }

    val supplier =
      clientService.findDefaultCodigSupplier().orElseThrow {
        IllegalStateException(
          "Aucun fournisseur Netstone par defaut n'est configure dans les societes internes"
        )
      }
    val order = sale.orderCodig ?: OrderCodig("", supplier, sale.saleDate)
    val codigCompany = clientService.findDefaultCodigCompany().orElse(null)

    order.client = supplier
    order.orderDate = sale.saleDate
    order.expectedDeliveryDate = sale.expectedDeliveryDate
    order.clientReference = sale.clientReference
    order.subject = sale.subject
    order.currency = sale.currency
    order.exchangeRate = sale.exchangeRate
    order.purchasePriceExclTax = sale.purchasePriceExclTax
    order.incoterms = codigCompany?.incoterm?.name
    order.incotermLocation = sale.client.deliveryPort
    order.paymentTerm = supplier.paymentTerm
    order.fiscalPosition = codigCompany?.fiscalPosition
    order.deliveryLocation = sale.client.deliveryPort
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
        overrideUnitPrice = { it.product?.purchasePriceExclTax },
      )

    savedOrder.recalculateTotals(generatedLines)
    orderCodigService.save(savedOrder)

    sale.orderCodig = savedOrder
    return salesCodigRepository.save(sale)
  }

  @Transactional(readOnly = true)
  fun findLines(saleId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.SALES_CODIG, saleId)

  /**
   * Saves the sale with its line items, recalculates totals and purchase price, then syncs the
   * generated order.
   */
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
