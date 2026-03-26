package fr.axl.lvy.quote

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderARepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class QuoteServiceTest {

  @Autowired lateinit var quoteService: QuoteService
  @Autowired lateinit var quoteRepository: QuoteRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderARepository: OrderARepository

  private fun createClient(code: String): Client {
    val client = Client(code, "Test Client $code")
    return clientRepository.save(client)
  }

  private fun createQuote(number: String, client: Client, status: Quote.QuoteStatus): Quote {
    val quote = Quote(number, client, LocalDate.of(2026, 1, 15))
    quote.status = status
    return quoteRepository.save(quote)
  }

  @Test
  fun save_and_retrieve_quote() {
    val client = createClient("CLI-Q01")
    val quote = Quote("DEV-2026-0001", client, LocalDate.of(2026, 3, 1))
    quote.subject = "Test Quote"
    quote.clientReference = "REF-CLIENT"
    quoteService.save(quote)

    val found = quoteService.findById(quote.id!!)
    assertThat(found).isPresent
    assertThat(found.get().quoteNumber).isEqualTo("DEV-2026-0001")
    assertThat(found.get().subject).isEqualTo("Test Quote")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-Q02")
    val quote = createQuote("DEV-2026-0002", client, Quote.QuoteStatus.DRAFT)

    assertThat(quoteService.findAll()).anyMatch { it.quoteNumber == "DEV-2026-0002" }

    quoteService.delete(quote.id!!)
    quoteRepository.flush()

    assertThat(quoteService.findAll()).noneMatch { it.quoteNumber == "DEV-2026-0002" }
  }

  @Test
  fun isEditable_for_draft_and_sent() {
    val client = createClient("CLI-Q03")

    val draft = createQuote("DEV-EDIT-1", client, Quote.QuoteStatus.DRAFT)
    assertThat(draft.isEditable()).isTrue

    val sent = createQuote("DEV-EDIT-2", client, Quote.QuoteStatus.SENT)
    assertThat(sent.isEditable()).isTrue

    val accepted = createQuote("DEV-EDIT-3", client, Quote.QuoteStatus.ACCEPTED)
    assertThat(accepted.isEditable()).isFalse

    val refused = createQuote("DEV-EDIT-4", client, Quote.QuoteStatus.REFUSED)
    assertThat(refused.isEditable()).isFalse

    val expired = createQuote("DEV-EDIT-5", client, Quote.QuoteStatus.EXPIRED)
    assertThat(expired.isEditable()).isFalse
  }

  @Test
  fun recalculateTotals_sums_lines() {
    val client = createClient("CLI-Q04")
    val quote = createQuote("DEV-CALC-1", client, Quote.QuoteStatus.DRAFT)
    quoteRepository.flush()

    val line1 = DocumentLine(DocumentLine.DocumentType.QUOTE, quote.id!!, "Item A")
    line1.quantity = BigDecimal("2")
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    line1.recalculate()

    val line2 = DocumentLine(DocumentLine.DocumentType.QUOTE, quote.id!!, "Item B")
    line2.quantity = BigDecimal("3")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal("10.00")
    line2.vatRate = BigDecimal("20.00")
    line2.recalculate()

    quote.recalculateTotals(listOf(line1, line2))

    // line1: 2 * 100 = 200, vat = 40
    // line2: 3 * 50 * 0.9 = 135, vat = 27
    assertThat(quote.totalExclTax).isEqualByComparingTo("335.00")
    assertThat(quote.totalVat).isEqualByComparingTo("67.00")
    assertThat(quote.totalInclTax).isEqualByComparingTo("402.00")
  }

  @Test
  fun convertToOrderA_copies_details_and_lines() {
    val client = createClient("CLI-Q05")
    val quote = createQuote("DEV-CONV-1", client, Quote.QuoteStatus.SENT)
    quote.subject = "Quote to convert"
    quote.clientReference = "CR-001"
    quote.billingAddress = "123 Billing St"
    quote.shippingAddress = "456 Shipping Ave"
    quote.currency = "USD"
    quote.incoterms = "FOB"
    quoteRepository.saveAndFlush(quote)

    val line = DocumentLine(DocumentLine.DocumentType.QUOTE, quote.id!!, "Widget")
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("20.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    val order = quoteService.convertToOrderA(quote, "CA-2026-0001")

    assertThat(order.id).isNotNull
    assertThat(order.orderNumber).isEqualTo("CA-2026-0001")
    assertThat(order.client.id).isEqualTo(client.id)
    assertThat(order.subject).isEqualTo("Quote to convert")
    assertThat(order.clientReference).isEqualTo("CR-001")
    assertThat(order.billingAddress).isEqualTo("123 Billing St")
    assertThat(order.shippingAddress).isEqualTo("456 Shipping Ave")
    assertThat(order.currency).isEqualTo("USD")
    assertThat(order.incoterms).isEqualTo("FOB")
    assertThat(order.quote!!.id).isEqualTo(quote.id)

    // Lines copied
    val orderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        order.id!!,
      )
    assertThat(orderLines).hasSize(1)
    assertThat(orderLines[0].designation).isEqualTo("Widget")
    assertThat(orderLines[0].quantity).isEqualByComparingTo("5")
    assertThat(orderLines[0].lineTotalExclTax).isEqualByComparingTo("100.00")

    // Quote status updated
    val updatedQuote = quoteRepository.findById(quote.id!!).orElseThrow()
    assertThat(updatedQuote.status).isEqualTo(Quote.QuoteStatus.ACCEPTED)

    // Order totals recalculated
    assertThat(order.totalExclTax).isEqualByComparingTo("100.00")
  }

  @Test
  fun convertToOrderA_from_accepted_quote_works() {
    val client = createClient("CLI-Q06")
    val quote = createQuote("DEV-CONV-2", client, Quote.QuoteStatus.ACCEPTED)
    quoteRepository.flush()

    val order = quoteService.convertToOrderA(quote, "CA-2026-0002")
    assertThat(order.id).isNotNull
  }

  @Test
  fun convertToOrderA_from_draft_quote_fails() {
    val client = createClient("CLI-Q07")
    val quote = createQuote("DEV-CONV-3", client, Quote.QuoteStatus.DRAFT)

    assertThatThrownBy { quoteService.convertToOrderA(quote, "CA-2026-0003") }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun convertToOrderA_from_refused_quote_fails() {
    val client = createClient("CLI-Q08")
    val quote = createQuote("DEV-CONV-4", client, Quote.QuoteStatus.REFUSED)

    assertThatThrownBy { quoteService.convertToOrderA(quote, "CA-2026-0004") }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun convertToOrderA_from_expired_quote_fails() {
    val client = createClient("CLI-Q09")
    val quote = createQuote("DEV-CONV-5", client, Quote.QuoteStatus.EXPIRED)

    assertThatThrownBy { quoteService.convertToOrderA(quote, "CA-2026-0005") }
      .isInstanceOf(IllegalStateException::class.java)
  }
}
