package fr.axl.lvy.order

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class OrderBServiceTest {

  @Autowired lateinit var orderBService: OrderBService
  @Autowired lateinit var orderBRepository: OrderBRepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createOrderA(number: String): OrderA {
    val client = clientRepository.save(Client("CLI-OB-$number", "Client"))
    val order = OrderA(number, client, LocalDate.of(2026, 3, 1))
    return orderARepository.save(order)
  }

  private fun createOrderB(number: String, orderA: OrderA, status: OrderB.OrderBStatus): OrderB {
    val order = OrderB(number, orderA)
    order.status = status
    return orderBRepository.save(order)
  }

  @Test
  fun save_and_retrieve_orderB() {
    val orderA = createOrderA("CA-OB-01")
    val orderB = OrderB("CB-2026-0001", orderA)
    orderBService.save(orderB)

    val found = orderBService.findById(orderB.id!!)
    assertThat(found).isPresent
    assertThat(found.get().orderNumber).isEqualTo("CB-2026-0001")
    assertThat(found.get().orderA!!.id).isEqualTo(orderA.id)
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val orderA = createOrderA("CA-OB-02")
    val orderB = createOrderB("CB-DEL-01", orderA, OrderB.OrderBStatus.SENT)

    orderBService.delete(orderB.id!!)
    orderBRepository.flush()

    assertThat(orderBService.findAll()).noneMatch { it.orderNumber == "CB-DEL-01" }
  }

  @Test
  fun status_transition_sent_to_confirmed() {
    val orderA = createOrderA("CA-OB-03")
    val orderB = createOrderB("CB-ST-01", orderA, OrderB.OrderBStatus.SENT)

    val updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.CONFIRMED)
    assertThat(updated.status).isEqualTo(OrderB.OrderBStatus.CONFIRMED)
  }

  @Test
  fun status_transition_confirmed_to_in_production() {
    val orderA = createOrderA("CA-OB-04")
    val orderB = createOrderB("CB-ST-02", orderA, OrderB.OrderBStatus.CONFIRMED)

    val updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.IN_PRODUCTION)
    assertThat(updated.status).isEqualTo(OrderB.OrderBStatus.IN_PRODUCTION)
  }

  @Test
  fun status_transition_in_production_to_received() {
    val orderA = createOrderA("CA-OB-05")
    val orderB = createOrderB("CB-ST-03", orderA, OrderB.OrderBStatus.IN_PRODUCTION)

    val updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.RECEIVED)
    assertThat(updated.status).isEqualTo(OrderB.OrderBStatus.RECEIVED)
  }

  @Test
  fun status_transition_sent_to_cancelled() {
    val orderA = createOrderA("CA-OB-06")
    val orderB = createOrderB("CB-ST-04", orderA, OrderB.OrderBStatus.SENT)

    val updated = orderBService.changeStatus(orderB, OrderB.OrderBStatus.CANCELLED)
    assertThat(updated.status).isEqualTo(OrderB.OrderBStatus.CANCELLED)
  }

  @Test
  fun invalid_status_transition_throws() {
    val orderA = createOrderA("CA-OB-07")
    val orderB = createOrderB("CB-ST-05", orderA, OrderB.OrderBStatus.SENT)

    assertThatThrownBy { orderBService.changeStatus(orderB, OrderB.OrderBStatus.RECEIVED) }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @ParameterizedTest
  @EnumSource(value = OrderB.OrderBStatus::class, names = ["RECEIVED", "CANCELLED"])
  fun no_transitions_from_terminal_statuses(terminal: OrderB.OrderBStatus) {
    val orderA = createOrderA("CA-OB-T${terminal.ordinal}")
    val orderB = createOrderB("CB-TERM-${terminal.ordinal}", orderA, terminal)

    assertThatThrownBy { orderBService.changeStatus(orderB, OrderB.OrderBStatus.CONFIRMED) }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun markReceived_sets_reception_data() {
    val orderA = createOrderA("CA-OB-08")
    val orderB = createOrderB("CB-REC-01", orderA, OrderB.OrderBStatus.IN_PRODUCTION)

    val received =
      orderBService.markReceived(orderB, LocalDate.of(2026, 4, 1), true, "All items conform")

    assertThat(received.status).isEqualTo(OrderB.OrderBStatus.RECEIVED)
    assertThat(received.receptionDate).isEqualTo(LocalDate.of(2026, 4, 1))
    assertThat(received.receptionConforming).isTrue
    assertThat(received.receptionReserve).isEqualTo("All items conform")
  }

  @Test
  fun markReceived_with_non_conforming_reception() {
    val orderA = createOrderA("CA-OB-09")
    val orderB = createOrderB("CB-REC-02", orderA, OrderB.OrderBStatus.CONFIRMED)

    val received =
      orderBService.markReceived(orderB, LocalDate.of(2026, 4, 2), false, "3 items damaged")

    assertThat(received.receptionConforming).isFalse
    assertThat(received.receptionReserve).isEqualTo("3 items damaged")
  }

  @Test
  fun findByOrderAId_returns_linked_orders() {
    val orderA = createOrderA("CA-OB-10")
    createOrderB("CB-LINK-01", orderA, OrderB.OrderBStatus.SENT)

    val found = orderBRepository.findByOrderAId(orderA.id!!)
    assertThat(found).hasSize(1)
    assertThat(found[0].orderNumber).isEqualTo("CB-LINK-01")
  }

  @Test
  fun default_status_is_SENT() {
    val orderA = createOrderA("CA-OB-11")
    val orderB = OrderB("CB-DEF-01", orderA)
    assertThat(orderB.status).isEqualTo(OrderB.OrderBStatus.SENT)
  }
}
