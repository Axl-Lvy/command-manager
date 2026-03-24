package fr.axl.lvy.quote;

import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderARepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

  private final QuoteRepository quoteRepository;
  private final OrderARepository orderARepository;
  private final DocumentLineRepository documentLineRepository;

  QuoteService(
      QuoteRepository quoteRepository,
      OrderARepository orderARepository,
      DocumentLineRepository documentLineRepository) {
    this.quoteRepository = quoteRepository;
    this.orderARepository = orderARepository;
    this.documentLineRepository = documentLineRepository;
  }

  @Transactional(readOnly = true)
  public List<Quote> findAll() {
    return quoteRepository.findByDeletedAtIsNull();
  }

  @Transactional(readOnly = true)
  public Optional<Quote> findById(Long id) {
    return quoteRepository.findById(id);
  }

  @Transactional
  public Quote save(Quote quote) {
    return quoteRepository.save(quote);
  }

  @Transactional
  public void delete(Long id) {
    quoteRepository.findById(id).ifPresent(Quote::softDelete);
  }

  @Transactional
  public OrderA convertToOrderA(Quote quote, String orderNumber) {
    if (quote.getStatus() != Quote.QuoteStatus.ACCEPTED
        && quote.getStatus() != Quote.QuoteStatus.SENT) {
      throw new IllegalStateException("Quote cannot be converted in its current status");
    }

    var order = new OrderA(orderNumber, quote.getClient(), LocalDate.now());
    order.setQuote(quote);
    order.setClientReference(quote.getClientReference());
    order.setSubject(quote.getSubject());
    order.setBillingAddress(quote.getBillingAddress());
    order.setShippingAddress(quote.getShippingAddress());
    order.setCurrency(quote.getCurrency());
    order.setExchangeRate(quote.getExchangeRate());
    order.setIncoterms(quote.getIncoterms());
    order.setNotes(quote.getNotes());
    order.setConditions(quote.getConditions());
    order = orderARepository.save(order);

    var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.QUOTE, quote.getId());
    for (var line : lines) {
      var newLine =
          new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), line.getDesignation());
      newLine.setProduct(line.getProduct());
      newLine.setDescription(line.getDescription());
      newLine.setHsCode(line.getHsCode());
      newLine.setMadeIn(line.getMadeIn());
      newLine.setClientProductCode(line.getClientProductCode());
      newLine.setQuantity(line.getQuantity());
      newLine.setUnit(line.getUnit());
      newLine.setUnitPriceExclTax(line.getUnitPriceExclTax());
      newLine.setDiscountPercent(line.getDiscountPercent());
      newLine.setVatRate(line.getVatRate());
      newLine.setPosition(line.getPosition());
      newLine.recalculate();
      documentLineRepository.save(newLine);
    }

    quote.setStatus(Quote.QuoteStatus.ACCEPTED);
    quoteRepository.save(quote);

    order.recalculateTotals();
    return orderARepository.save(order);
  }
}
