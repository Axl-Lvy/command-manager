package fr.axl.lvy.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientRepository;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.order.OrderARepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class QuoteServiceTest {

  @Autowired QuoteService quoteService;
  @Autowired QuoteRepository quoteRepository;
  @Autowired ClientRepository clientRepository;
  @Autowired DocumentLineRepository documentLineRepository;
  @Autowired OrderARepository orderARepository;

  private Client createClient(String code) {
    var client = new Client(code, "Test Client " + code);
    return clientRepository.save(client);
  }

  private Quote createQuote(String number, Client client, Quote.QuoteStatus status) {
    var quote = new Quote(number, client, LocalDate.of(2026, 1, 15));
    quote.setStatus(status);
    return quoteRepository.save(quote);
  }

  @Test
  void save_and_retrieve_quote() {
    var client = createClient("CLI-Q01");
    var quote = new Quote("DEV-2026-0001", client, LocalDate.of(2026, 3, 1));
    quote.setSubject("Test Quote");
    quote.setClientReference("REF-CLIENT");
    quoteService.save(quote);

    var found = quoteService.findById(quote.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getQuoteNumber()).isEqualTo("DEV-2026-0001");
    assertThat(found.get().getSubject()).isEqualTo("Test Quote");
  }

  @Test
  void soft_delete_excludes_from_findAll() {
    var client = createClient("CLI-Q02");
    var quote = createQuote("DEV-2026-0002", client, Quote.QuoteStatus.DRAFT);

    assertThat(quoteService.findAll()).anyMatch(q -> q.getQuoteNumber().equals("DEV-2026-0002"));

    quoteService.delete(quote.getId());
    quoteRepository.flush();

    assertThat(quoteService.findAll()).noneMatch(q -> q.getQuoteNumber().equals("DEV-2026-0002"));
  }

  @Test
  void isEditable_for_draft_and_sent() {
    var client = createClient("CLI-Q03");

    var draft = createQuote("DEV-EDIT-1", client, Quote.QuoteStatus.DRAFT);
    assertThat(draft.isEditable()).isTrue();

    var sent = createQuote("DEV-EDIT-2", client, Quote.QuoteStatus.SENT);
    assertThat(sent.isEditable()).isTrue();

    var accepted = createQuote("DEV-EDIT-3", client, Quote.QuoteStatus.ACCEPTED);
    assertThat(accepted.isEditable()).isFalse();

    var refused = createQuote("DEV-EDIT-4", client, Quote.QuoteStatus.REFUSED);
    assertThat(refused.isEditable()).isFalse();

    var expired = createQuote("DEV-EDIT-5", client, Quote.QuoteStatus.EXPIRED);
    assertThat(expired.isEditable()).isFalse();
  }

  @Test
  void recalculateTotals_sums_lines() {
    var client = createClient("CLI-Q04");
    var quote = createQuote("DEV-CALC-1", client, Quote.QuoteStatus.DRAFT);
    quoteRepository.flush();

    var line1 = new DocumentLine(DocumentLine.DocumentType.QUOTE, quote.getId(), "Item A");
    line1.setQuantity(new BigDecimal("2"));
    line1.setUnitPriceExclTax(new BigDecimal("100.00"));
    line1.setDiscountPercent(BigDecimal.ZERO);
    line1.setVatRate(new BigDecimal("20.00"));
    line1.recalculate();

    var line2 = new DocumentLine(DocumentLine.DocumentType.QUOTE, quote.getId(), "Item B");
    line2.setQuantity(new BigDecimal("3"));
    line2.setUnitPriceExclTax(new BigDecimal("50.00"));
    line2.setDiscountPercent(new BigDecimal("10.00"));
    line2.setVatRate(new BigDecimal("20.00"));
    line2.recalculate();

    quote.recalculateTotals(List.of(line1, line2));

    // line1: 2 * 100 = 200, vat = 40
    // line2: 3 * 50 * 0.9 = 135, vat = 27
    assertThat(quote.getTotalExclTax()).isEqualByComparingTo("335.00");
    assertThat(quote.getTotalVat()).isEqualByComparingTo("67.00");
    assertThat(quote.getTotalInclTax()).isEqualByComparingTo("402.00");
  }

  @Test
  void convertToOrderA_copies_details_and_lines() {
    var client = createClient("CLI-Q05");
    var quote = createQuote("DEV-CONV-1", client, Quote.QuoteStatus.SENT);
    quote.setSubject("Quote to convert");
    quote.setClientReference("CR-001");
    quote.setBillingAddress("123 Billing St");
    quote.setShippingAddress("456 Shipping Ave");
    quote.setCurrency("USD");
    quote.setIncoterms("FOB");
    quoteRepository.saveAndFlush(quote);

    var line = new DocumentLine(DocumentLine.DocumentType.QUOTE, quote.getId(), "Widget");
    line.setQuantity(new BigDecimal("5"));
    line.setUnitPriceExclTax(new BigDecimal("20.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));
    line.setPosition(0);
    line.recalculate();
    documentLineRepository.saveAndFlush(line);

    var order = quoteService.convertToOrderA(quote, "CA-2026-0001");

    assertThat(order.getId()).isNotNull();
    assertThat(order.getOrderNumber()).isEqualTo("CA-2026-0001");
    assertThat(order.getClient().getId()).isEqualTo(client.getId());
    assertThat(order.getSubject()).isEqualTo("Quote to convert");
    assertThat(order.getClientReference()).isEqualTo("CR-001");
    assertThat(order.getBillingAddress()).isEqualTo("123 Billing St");
    assertThat(order.getShippingAddress()).isEqualTo("456 Shipping Ave");
    assertThat(order.getCurrency()).isEqualTo("USD");
    assertThat(order.getIncoterms()).isEqualTo("FOB");
    assertThat(order.getQuote().getId()).isEqualTo(quote.getId());

    // Lines copied
    var orderLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_A, order.getId());
    assertThat(orderLines).hasSize(1);
    assertThat(orderLines.get(0).getDesignation()).isEqualTo("Widget");
    assertThat(orderLines.get(0).getQuantity()).isEqualByComparingTo("5");
    assertThat(orderLines.get(0).getLineTotalExclTax()).isEqualByComparingTo("100.00");

    // Quote status updated
    var updatedQuote = quoteRepository.findById(quote.getId()).orElseThrow();
    assertThat(updatedQuote.getStatus()).isEqualTo(Quote.QuoteStatus.ACCEPTED);

    // Order totals recalculated
    assertThat(order.getTotalExclTax()).isEqualByComparingTo("100.00");
  }

  @Test
  void convertToOrderA_from_accepted_quote_works() {
    var client = createClient("CLI-Q06");
    var quote = createQuote("DEV-CONV-2", client, Quote.QuoteStatus.ACCEPTED);
    quoteRepository.flush();

    var order = quoteService.convertToOrderA(quote, "CA-2026-0002");
    assertThat(order.getId()).isNotNull();
  }

  @Test
  void convertToOrderA_from_draft_quote_fails() {
    var client = createClient("CLI-Q07");
    var quote = createQuote("DEV-CONV-3", client, Quote.QuoteStatus.DRAFT);

    assertThatThrownBy(() -> quoteService.convertToOrderA(quote, "CA-2026-0003"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void convertToOrderA_from_refused_quote_fails() {
    var client = createClient("CLI-Q08");
    var quote = createQuote("DEV-CONV-4", client, Quote.QuoteStatus.REFUSED);

    assertThatThrownBy(() -> quoteService.convertToOrderA(quote, "CA-2026-0004"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void convertToOrderA_from_expired_quote_fails() {
    var client = createClient("CLI-Q09");
    var quote = createQuote("DEV-CONV-5", client, Quote.QuoteStatus.EXPIRED);

    assertThatThrownBy(() -> quoteService.convertToOrderA(quote, "CA-2026-0005"))
        .isInstanceOf(IllegalStateException.class);
  }
}
