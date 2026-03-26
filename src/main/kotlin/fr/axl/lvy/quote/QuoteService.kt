package fr.axl.lvy.quote

import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import java.time.LocalDate
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuoteService(
  private val quoteRepository: QuoteRepository,
  private val orderARepository: OrderARepository,
  private val documentLineRepository: DocumentLineRepository,
) {
  @Transactional(readOnly = true)
  fun findAll(): List<Quote> = quoteRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Quote> = quoteRepository.findById(id)

  @Transactional fun save(quote: Quote): Quote = quoteRepository.save(quote)

  @Transactional
  fun delete(id: Long) {
    quoteRepository.findById(id).ifPresent { it.softDelete() }
  }

  @Transactional
  fun convertToOrderA(quote: Quote, orderNumber: String): OrderA {
    if (quote.status != Quote.QuoteStatus.ACCEPTED && quote.status != Quote.QuoteStatus.SENT) {
      throw IllegalStateException("Quote cannot be converted in its current status")
    }

    var order = OrderA(orderNumber, quote.client, LocalDate.now())
    order.quote = quote
    order.clientReference = quote.clientReference
    order.subject = quote.subject
    order.billingAddress = quote.billingAddress
    order.shippingAddress = quote.shippingAddress
    order.currency = quote.currency
    order.exchangeRate = quote.exchangeRate
    order.incoterms = quote.incoterms
    order.notes = quote.notes
    order.conditions = quote.conditions
    order = orderARepository.save(order)

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.QUOTE,
        quote.id!!,
      )
    for (line in lines) {
      val newLine = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, line.designation)
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

    quote.status = Quote.QuoteStatus.ACCEPTED
    quoteRepository.save(quote)

    val orderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        order.id!!,
      )
    order.recalculateTotals(orderLines)
    return orderARepository.save(order)
  }
}
