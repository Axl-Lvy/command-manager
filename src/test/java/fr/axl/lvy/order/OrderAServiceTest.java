package fr.axl.lvy.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientRepository;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.product.Product;
import fr.axl.lvy.product.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class OrderAServiceTest {

  @Autowired OrderAService orderAService;
  @Autowired OrderARepository orderARepository;
  @Autowired OrderBRepository orderBRepository;
  @Autowired ClientRepository clientRepository;
  @Autowired ProductRepository productRepository;
  @Autowired DocumentLineRepository documentLineRepository;

  private Client createClient(String code) {
    return clientRepository.save(new Client(code, "Client " + code));
  }

  private OrderA createOrderA(String number, Client client, OrderA.OrderAStatus status) {
    var order = new OrderA(number, client, LocalDate.of(2026, 3, 1));
    order.setStatus(status);
    return orderARepository.save(order);
  }

  @Test
  void save_and_retrieve_order() {
    var client = createClient("CLI-OA01");
    var order = new OrderA("CA-2026-0001", client, LocalDate.of(2026, 3, 1));
    order.setSubject("Test Order");
    orderAService.save(order);

    var found = orderAService.findById(order.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getOrderNumber()).isEqualTo("CA-2026-0001");
  }

  @Test
  void soft_delete_excludes_from_findAll() {
    var client = createClient("CLI-OA02");
    var order = createOrderA("CA-DEL-001", client, OrderA.OrderAStatus.CONFIRMED);

    orderAService.delete(order.getId());
    orderARepository.flush();

    assertThat(orderAService.findAll()).noneMatch(o -> o.getOrderNumber().equals("CA-DEL-001"));
  }

  @Test
  void isEditable_for_confirmed_in_production_ready() {
    var client = createClient("CLI-OA03");

    assertThat(createOrderA("CA-E1", client, OrderA.OrderAStatus.CONFIRMED).isEditable()).isTrue();
    assertThat(createOrderA("CA-E2", client, OrderA.OrderAStatus.IN_PRODUCTION).isEditable())
        .isTrue();
    assertThat(createOrderA("CA-E3", client, OrderA.OrderAStatus.READY).isEditable()).isTrue();
    assertThat(createOrderA("CA-E4", client, OrderA.OrderAStatus.DELIVERED).isEditable()).isFalse();
    assertThat(createOrderA("CA-E5", client, OrderA.OrderAStatus.INVOICED).isEditable()).isFalse();
    assertThat(createOrderA("CA-E6", client, OrderA.OrderAStatus.CANCELLED).isEditable()).isFalse();
  }

  @Test
  void status_transition_confirmed_to_in_production() {
    var client = createClient("CLI-OA04");
    var order = createOrderA("CA-ST-01", client, OrderA.OrderAStatus.CONFIRMED);

    var updated = orderAService.changeStatus(order, OrderA.OrderAStatus.IN_PRODUCTION);
    assertThat(updated.getStatus()).isEqualTo(OrderA.OrderAStatus.IN_PRODUCTION);
  }

  @Test
  void status_transition_in_production_to_ready() {
    var client = createClient("CLI-OA05");
    var order = createOrderA("CA-ST-02", client, OrderA.OrderAStatus.IN_PRODUCTION);

    var updated = orderAService.changeStatus(order, OrderA.OrderAStatus.READY);
    assertThat(updated.getStatus()).isEqualTo(OrderA.OrderAStatus.READY);
  }

  @Test
  void status_transition_ready_to_delivered() {
    var client = createClient("CLI-OA06");
    var order = createOrderA("CA-ST-03", client, OrderA.OrderAStatus.READY);

    var updated = orderAService.changeStatus(order, OrderA.OrderAStatus.DELIVERED);
    assertThat(updated.getStatus()).isEqualTo(OrderA.OrderAStatus.DELIVERED);
  }

  @Test
  void status_transition_delivered_to_invoiced() {
    var client = createClient("CLI-OA07");
    var order = createOrderA("CA-ST-04", client, OrderA.OrderAStatus.DELIVERED);

    var updated = orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED);
    assertThat(updated.getStatus()).isEqualTo(OrderA.OrderAStatus.INVOICED);
  }

  @Test
  void status_transition_confirmed_to_cancelled() {
    var client = createClient("CLI-OA08");
    var order = createOrderA("CA-ST-05", client, OrderA.OrderAStatus.CONFIRMED);

    var updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CANCELLED);
    assertThat(updated.getStatus()).isEqualTo(OrderA.OrderAStatus.CANCELLED);
  }

  @Test
  void invalid_status_transition_throws() {
    var client = createClient("CLI-OA09");
    var order = createOrderA("CA-ST-06", client, OrderA.OrderAStatus.CONFIRMED);

    assertThatThrownBy(() -> orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderA.OrderAStatus.class,
      names = {"INVOICED", "CANCELLED"})
  void no_transitions_from_terminal_statuses(OrderA.OrderAStatus terminal) {
    var client = createClient("CLI-OA-T" + terminal.ordinal());
    var order = createOrderA("CA-TERM-" + terminal.ordinal(), client, terminal);

    assertThatThrownBy(() -> orderAService.changeStatus(order, OrderA.OrderAStatus.CONFIRMED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void recalculateTotals_includes_margin() {
    var client = createClient("CLI-OA10");
    var order = createOrderA("CA-CALC-1", client, OrderA.OrderAStatus.CONFIRMED);
    order.setPurchasePriceExclTax(new BigDecimal("300.00"));
    orderARepository.flush();

    var line = new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), "Item");
    line.setQuantity(new BigDecimal("5"));
    line.setUnitPriceExclTax(new BigDecimal("100.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));
    line.recalculate();

    order.recalculateTotals(List.of(line));

    assertThat(order.getTotalExclTax()).isEqualByComparingTo("500.00");
    assertThat(order.getTotalVat()).isEqualByComparingTo("100.00");
    assertThat(order.getTotalInclTax()).isEqualByComparingTo("600.00");
    assertThat(order.getMarginExclTax()).isEqualByComparingTo("200.00"); // 500 - 300
  }

  @Test
  void duplicate_copies_order_and_lines() {
    var client = createClient("CLI-OA11");
    var order = createOrderA("CA-DUP-1", client, OrderA.OrderAStatus.CONFIRMED);
    order.setSubject("Original");
    order.setClientReference("CR-DUP");
    order.setBillingAddress("Billing Addr");
    order.setCurrency("USD");
    orderARepository.saveAndFlush(order);

    var line = new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), "Widget");
    line.setQuantity(new BigDecimal("10"));
    line.setUnitPriceExclTax(new BigDecimal("25.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));
    line.setPosition(0);
    line.recalculate();
    documentLineRepository.saveAndFlush(line);

    var copy = orderAService.duplicate(order, "CA-DUP-2");

    assertThat(copy.getId()).isNotEqualTo(order.getId());
    assertThat(copy.getOrderNumber()).isEqualTo("CA-DUP-2");
    assertThat(copy.getSubject()).isEqualTo("Original");
    assertThat(copy.getClientReference()).isEqualTo("CR-DUP");
    assertThat(copy.getCurrency()).isEqualTo("USD");
    assertThat(copy.getSourceOrder().getId()).isEqualTo(order.getId());
    assertThat(copy.getStatus()).isEqualTo(OrderA.OrderAStatus.CONFIRMED);

    var copyLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_A, copy.getId());
    assertThat(copyLines).hasSize(1);
    assertThat(copyLines.get(0).getDesignation()).isEqualTo("Widget");
    assertThat(copyLines.get(0).getQuantity()).isEqualByComparingTo("10");
    assertThat(copyLines.get(0).getLineTotalExclTax()).isEqualByComparingTo("250.00");
  }

  @Test
  void handleMto_creates_orderB_for_mto_products() {
    var client = createClient("CLI-OA12");
    var order = createOrderA("CA-MTO-1", client, OrderA.OrderAStatus.CONFIRMED);
    orderARepository.flush();

    var mtoProduct = new Product("PRD-MTO", "Custom Part");
    mtoProduct.setType(Product.ProductType.PRODUCT);
    mtoProduct.setMto(true);
    mtoProduct.setSellingPriceExclTax(new BigDecimal("100.00"));
    mtoProduct.setPurchasePriceExclTax(new BigDecimal("60.00"));
    productRepository.saveAndFlush(mtoProduct);

    var regularProduct = new Product("PRD-REG", "Standard Part");
    regularProduct.setType(Product.ProductType.PRODUCT);
    regularProduct.setMto(false);
    regularProduct.setSellingPriceExclTax(new BigDecimal("50.00"));
    regularProduct.setPurchasePriceExclTax(new BigDecimal("30.00"));
    productRepository.saveAndFlush(regularProduct);

    var mtoLine = new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), "Custom Part");
    mtoLine.setProduct(mtoProduct);
    mtoLine.setQuantity(new BigDecimal("3"));
    mtoLine.setUnitPriceExclTax(new BigDecimal("100.00"));
    mtoLine.setDiscountPercent(BigDecimal.ZERO);
    mtoLine.setVatRate(new BigDecimal("20.00"));
    mtoLine.setPosition(0);
    mtoLine.recalculate();
    documentLineRepository.saveAndFlush(mtoLine);

    var regularLine =
        new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), "Standard Part");
    regularLine.setProduct(regularProduct);
    regularLine.setQuantity(new BigDecimal("2"));
    regularLine.setUnitPriceExclTax(new BigDecimal("50.00"));
    regularLine.setDiscountPercent(BigDecimal.ZERO);
    regularLine.setVatRate(new BigDecimal("20.00"));
    regularLine.setPosition(1);
    regularLine.recalculate();
    documentLineRepository.saveAndFlush(regularLine);

    orderAService.handleMto(order, "CB-2026-0001");

    var updatedOrder = orderARepository.findById(order.getId()).orElseThrow();
    assertThat(updatedOrder.getOrderB()).isNotNull();

    var orderB = updatedOrder.getOrderB();
    assertThat(orderB.getOrderNumber()).isEqualTo("CB-2026-0001");
    assertThat(orderB.getOrderA().getId()).isEqualTo(order.getId());

    // Only MTO lines copied, at purchase price
    var orderBLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_B, orderB.getId());
    assertThat(orderBLines).hasSize(1);
    assertThat(orderBLines.get(0).getDesignation()).isEqualTo("Custom Part");
    assertThat(orderBLines.get(0).getUnitPriceExclTax()).isEqualByComparingTo("60.00");
    assertThat(orderBLines.get(0).getQuantity()).isEqualByComparingTo("3");
  }

  @Test
  void handleMto_does_nothing_without_mto_products() {
    var client = createClient("CLI-OA13");
    var order = createOrderA("CA-MTO-2", client, OrderA.OrderAStatus.CONFIRMED);
    orderARepository.flush();

    var regularProduct = new Product("PRD-REG2", "Standard");
    regularProduct.setMto(false);
    regularProduct.setSellingPriceExclTax(new BigDecimal("50.00"));
    productRepository.saveAndFlush(regularProduct);

    var line = new DocumentLine(DocumentLine.DocumentType.ORDER_A, order.getId(), "Standard");
    line.setProduct(regularProduct);
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPriceExclTax(new BigDecimal("50.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(BigDecimal.ZERO);
    line.setPosition(0);
    line.recalculate();
    documentLineRepository.saveAndFlush(line);

    orderAService.handleMto(order, "CB-2026-0002");

    var updatedOrder = orderARepository.findById(order.getId()).orElseThrow();
    assertThat(updatedOrder.getOrderB()).isNull();
    assertThat(orderBRepository.findAll()).isEmpty();
  }
}
