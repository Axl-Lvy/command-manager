package fr.axl.lvy.order;

import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.product.Product;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderAService {

  private static final Set<OrderA.OrderAStatus> ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      Set.of(OrderA.OrderAStatus.IN_PRODUCTION, OrderA.OrderAStatus.CANCELLED);
  private static final Set<OrderA.OrderAStatus> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION =
      Set.of(OrderA.OrderAStatus.READY, OrderA.OrderAStatus.CANCELLED);
  private static final Set<OrderA.OrderAStatus> ALLOWED_TRANSITIONS_FROM_READY =
      Set.of(OrderA.OrderAStatus.DELIVERED, OrderA.OrderAStatus.CANCELLED);
  private static final Set<OrderA.OrderAStatus> ALLOWED_TRANSITIONS_FROM_DELIVERED =
      Set.of(OrderA.OrderAStatus.INVOICED);

  private final OrderARepository orderARepository;
  private final OrderBRepository orderBRepository;
  private final DocumentLineRepository documentLineRepository;

  OrderAService(
      OrderARepository orderARepository,
      OrderBRepository orderBRepository,
      DocumentLineRepository documentLineRepository) {
    this.orderARepository = orderARepository;
    this.orderBRepository = orderBRepository;
    this.documentLineRepository = documentLineRepository;
  }

  @Transactional(readOnly = true)
  public List<OrderA> findAll() {
    return orderARepository.findByDeletedAtIsNull();
  }

  @Transactional(readOnly = true)
  public Optional<OrderA> findById(Long id) {
    return orderARepository.findById(id);
  }

  @Transactional
  public OrderA save(OrderA order) {
    return orderARepository.save(order);
  }

  @Transactional
  public void delete(Long id) {
    orderARepository.findById(id).ifPresent(OrderA::softDelete);
  }

  @Transactional
  public OrderA changeStatus(OrderA order, OrderA.OrderAStatus newStatus) {
    var allowed = getAllowedTransitions(order.getStatus());
    if (!allowed.contains(newStatus)) {
      throw new IllegalStateException(
          "Cannot transition from " + order.getStatus() + " to " + newStatus);
    }
    order.setStatus(newStatus);
    return orderARepository.save(order);
  }

  @Transactional
  public OrderA duplicate(OrderA source, String newOrderNumber) {
    var copy = new OrderA(newOrderNumber, source.getClient(), source.getOrderDate());
    copy.setClientReference(source.getClientReference());
    copy.setSubject(source.getSubject());
    copy.setBillingAddress(source.getBillingAddress());
    copy.setShippingAddress(source.getShippingAddress());
    copy.setCurrency(source.getCurrency());
    copy.setExchangeRate(source.getExchangeRate());
    copy.setIncoterms(source.getIncoterms());
    copy.setNotes(source.getNotes());
    copy.setConditions(source.getConditions());
    copy.setSourceOrder(source);
    copy = orderARepository.save(copy);

    var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_A, source.getId());
    for (var line : lines) {
      var newLine =
          new DocumentLine(DocumentLine.DocumentType.ORDER_A, copy.getId(), line.getDesignation());
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

    copy.recalculateTotals();
    return orderARepository.save(copy);
  }

  @Transactional
  public void handleMto(OrderA order, String orderBNumber) {
    var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_A, order.getId());
    var mtoLines =
        lines.stream().filter(l -> l.getProduct() != null && l.getProduct().isMto()).toList();

    if (mtoLines.isEmpty()) {
      return;
    }

    var orderB = new OrderB(orderBNumber, order);
    orderB = orderBRepository.save(orderB);

    for (var line : mtoLines) {
      Product product = line.getProduct();
      var newLine =
          new DocumentLine(
              DocumentLine.DocumentType.ORDER_B, orderB.getId(), line.getDesignation());
      newLine.setProduct(product);
      newLine.setDescription(line.getDescription());
      newLine.setHsCode(line.getHsCode());
      newLine.setMadeIn(line.getMadeIn());
      newLine.setClientProductCode(line.getClientProductCode());
      newLine.setQuantity(line.getQuantity());
      newLine.setUnit(line.getUnit());
      newLine.setUnitPriceExclTax(product.getPurchasePriceExclTax());
      newLine.setDiscountPercent(line.getDiscountPercent());
      newLine.setVatRate(line.getVatRate());
      newLine.setPosition(line.getPosition());
      newLine.recalculate();
      documentLineRepository.save(newLine);
    }

    orderB.recalculateTotals();
    orderB = orderBRepository.save(orderB);

    order.setOrderB(orderB);
    orderARepository.save(order);
  }

  private Set<OrderA.OrderAStatus> getAllowedTransitions(OrderA.OrderAStatus current) {
    return switch (current) {
      case CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED;
      case IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION;
      case READY -> ALLOWED_TRANSITIONS_FROM_READY;
      case DELIVERED -> ALLOWED_TRANSITIONS_FROM_DELIVERED;
      default -> Set.of();
    };
  }
}
