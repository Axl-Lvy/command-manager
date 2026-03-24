package fr.axl.lvy.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class OrderBServiceTest {

  @Autowired OrderBService orderBService;
  @Autowired OrderBRepository orderBRepository;
  @Autowired OrderARepository orderARepository;
  @Autowired ClientRepository clientRepository;

  private OrderA createOrderA(String number) {
    var client = clientRepository.save(new Client("CLI-OB-" + number, "Client"));
    var order = new OrderA(number, client, LocalDate.of(2026, 3, 1));
    return orderARepository.save(order);
  }

  private OrderB createOrderB(String number, OrderA orderA, OrderB.OrderBStatus status) {
    var order = new OrderB(number, orderA);
    order.setStatus(status);
    return orderBRepository.save(order);
  }

  @Test
  void save_and_retrieve_orderB() {
    var orderA = createOrderA("CA-OB-01");
    var orderB = new OrderB("CB-2026-0001", orderA);
    orderBService.save(orderB);

    var found = orderBService.findById(orderB.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getOrderNumber()).isEqualTo("CB-2026-0001");
    assertThat(found.get().getOrderA().getId()).isEqualTo(orderA.getId());
  }

  @Test
  void soft_delete_excludes_from_findAll() {
    var orderA = createOrderA("CA-OB-02");
    var orderB = createOrderB("CB-DEL-01", orderA, OrderB.OrderBStatus.SENT);

    orderBService.delete(orderB.getId());
    orderBRepository.flush();

    assertThat(orderBService.findAll()).noneMatch(o -> o.getOrderNumber().equals("CB-DEL-01"));
  }

  @Test
  void status_transition_sent_to_confirmed() {
    var orderA = createOrderA("CA-OB-03");
    var orderB = createOrderB("CB-ST-01", orderA, OrderB.OrderBStatus.SENT);

    var updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.CONFIRMED);
    assertThat(updated.getStatus()).isEqualTo(OrderB.OrderBStatus.CONFIRMED);
  }

  @Test
  void status_transition_confirmed_to_in_production() {
    var orderA = createOrderA("CA-OB-04");
    var orderB = createOrderB("CB-ST-02", orderA, OrderB.OrderBStatus.CONFIRMED);

    var updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.IN_PRODUCTION);
    assertThat(updated.getStatus()).isEqualTo(OrderB.OrderBStatus.IN_PRODUCTION);
  }

  @Test
  void status_transition_in_production_to_received() {
    var orderA = createOrderA("CA-OB-05");
    var orderB = createOrderB("CB-ST-03", orderA, OrderB.OrderBStatus.IN_PRODUCTION);

    var updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.RECEIVED);
    assertThat(updated.getStatus()).isEqualTo(OrderB.OrderBStatus.RECEIVED);
  }

  @Test
  void status_transition_sent_to_cancelled() {
    var orderA = createOrderA("CA-OB-06");
    var orderB = createOrderB("CB-ST-04", orderA, OrderB.OrderBStatus.SENT);

    var updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.CANCELLED);
    assertThat(updated.getStatus()).isEqualTo(OrderB.OrderBStatus.CANCELLED);
  }

  @Test
  void invalid_status_transition_throws() {
    var orderA = createOrderA("CA-OB-07");
    var orderB = createOrderB("CB-ST-05", orderA, OrderB.OrderBStatus.SENT);

    assertThatThrownBy(() -> orderBService.changeStatus(orderB, OrderB.OrderBStatus.RECEIVED))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderB.OrderBStatus.class,
      names = {"RECEIVED", "CANCELLED"})
  void no_transitions_from_terminal_statuses(OrderB.OrderBStatus terminal) {
    var orderA = createOrderA("CA-OB-T" + terminal.ordinal());
    var orderB = createOrderB("CB-TERM-" + terminal.ordinal(), orderA, terminal);

    assertThatThrownBy(() -> orderBService.changeStatus(orderB, OrderB.OrderBStatus.CONFIRMED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void markReceived_sets_reception_data() {
    var orderA = createOrderA("CA-OB-08");
    var orderB = createOrderB("CB-REC-01", orderA, OrderB.OrderBStatus.IN_PRODUCTION);

    var received =
        orderBService.markReceived(orderB, LocalDate.of(2026, 4, 1), true, "All items conform");

    assertThat(received.getStatus()).isEqualTo(OrderB.OrderBStatus.RECEIVED);
    assertThat(received.getReceptionDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(received.getReceptionConforming()).isTrue();
    assertThat(received.getReceptionReserve()).isEqualTo("All items conform");
  }

  @Test
  void markReceived_with_non_conforming_reception() {
    var orderA = createOrderA("CA-OB-09");
    var orderB = createOrderB("CB-REC-02", orderA, OrderB.OrderBStatus.CONFIRMED);

    var received =
        orderBService.markReceived(orderB, LocalDate.of(2026, 4, 2), false, "3 items damaged");

    assertThat(received.getReceptionConforming()).isFalse();
    assertThat(received.getReceptionReserve()).isEqualTo("3 items damaged");
  }

  @Test
  void findByOrderAId_returns_linked_orders() {
    var orderA = createOrderA("CA-OB-10");
    createOrderB("CB-LINK-01", orderA, OrderB.OrderBStatus.SENT);

    var found = orderBRepository.findByOrderAId(orderA.getId());
    assertThat(found).hasSize(1);
    assertThat(found.get(0).getOrderNumber()).isEqualTo("CB-LINK-01");
  }

  @Test
  void default_status_is_SENT() {
    var orderA = createOrderA("CA-OB-11");
    var orderB = new OrderB("CB-DEF-01", orderA);
    assertThat(orderB.getStatus()).isEqualTo(OrderB.OrderBStatus.SENT);
  }
}
