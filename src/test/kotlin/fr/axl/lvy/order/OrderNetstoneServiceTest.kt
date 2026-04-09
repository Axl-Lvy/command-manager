package fr.axl.lvy.order

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import java.math.BigDecimal
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
class OrderNetstoneServiceTest {

  @Autowired lateinit var orderNetstoneService: OrderNetstoneService
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createOrderCodig(number: String): OrderCodig {
    val client = clientRepository.save(Client("CLI-OB-$number", "Client"))
    val order = OrderCodig(number, client, LocalDate.of(2026, 3, 1))
    return orderCodigRepository.save(order)
  }

  private fun createOrderNetstone(
    number: String,
    orderCodig: OrderCodig,
    status: OrderNetstone.OrderNetstoneStatus,
  ): OrderNetstone {
    val order = OrderNetstone(number, orderCodig)
    order.status = status
    return orderNetstoneRepository.save(order)
  }

  @Test
  fun save_and_retrieve_orderNetstone() {
    val orderCodig = createOrderCodig("CA-OB-01")
    val orderNetstone = OrderNetstone("CB-2026-0001", orderCodig)
    orderNetstoneService.save(orderNetstone)

    val found = orderNetstoneService.findById(orderNetstone.id!!)
    assertThat(found).isPresent
    assertThat(found.get().orderNumber).isEqualTo("CB-2026-0001")
    assertThat(found.get().orderCodig!!.id).isEqualTo(orderCodig.id)
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val orderCodig = createOrderCodig("CA-OB-02")
    val orderNetstone =
      createOrderNetstone("CB-DEL-01", orderCodig, OrderNetstone.OrderNetstoneStatus.SENT)

    orderNetstoneService.delete(orderNetstone.id!!)
    orderNetstoneRepository.flush()

    assertThat(orderNetstoneService.findAll()).noneMatch { it.orderNumber == "CB-DEL-01" }
  }

  @Test
  fun status_transition_sent_to_confirmed() {
    val orderCodig = createOrderCodig("CA-OB-03")
    val orderNetstone =
      createOrderNetstone("CB-ST-01", orderCodig, OrderNetstone.OrderNetstoneStatus.SENT)

    val updated =
      orderNetstoneService.changeStatus(orderNetstone, OrderNetstone.OrderNetstoneStatus.CONFIRMED)
    assertThat(updated.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.CONFIRMED)
  }

  @Test
  fun status_transition_confirmed_to_in_production() {
    val orderCodig = createOrderCodig("CA-OB-04")
    val orderNetstone =
      createOrderNetstone("CB-ST-02", orderCodig, OrderNetstone.OrderNetstoneStatus.CONFIRMED)

    val updated =
      orderNetstoneService.changeStatus(
        orderNetstone,
        OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION,
      )
    assertThat(updated.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION)
  }

  @Test
  fun status_transition_in_production_to_received() {
    val orderCodig = createOrderCodig("CA-OB-05")
    val orderNetstone =
      createOrderNetstone("CB-ST-03", orderCodig, OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION)

    val updated =
      orderNetstoneService.changeStatus(orderNetstone, OrderNetstone.OrderNetstoneStatus.RECEIVED)
    assertThat(updated.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.RECEIVED)
  }

  @Test
  fun status_transition_sent_to_cancelled() {
    val orderCodig = createOrderCodig("CA-OB-06")
    val orderNetstone =
      createOrderNetstone("CB-ST-04", orderCodig, OrderNetstone.OrderNetstoneStatus.SENT)

    val updated =
      orderNetstoneService.changeStatus(orderNetstone, OrderNetstone.OrderNetstoneStatus.CANCELLED)
    assertThat(updated.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.CANCELLED)
  }

  @Test
  fun invalid_status_transition_throws() {
    val orderCodig = createOrderCodig("CA-OB-07")
    val orderNetstone =
      createOrderNetstone("CB-ST-05", orderCodig, OrderNetstone.OrderNetstoneStatus.SENT)

    assertThatThrownBy {
        orderNetstoneService.changeStatus(orderNetstone, OrderNetstone.OrderNetstoneStatus.RECEIVED)
      }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @ParameterizedTest
  @EnumSource(value = OrderNetstone.OrderNetstoneStatus::class, names = ["RECEIVED", "CANCELLED"])
  fun no_transitions_from_terminal_statuses(terminal: OrderNetstone.OrderNetstoneStatus) {
    val orderCodig = createOrderCodig("CA-OB-T${terminal.ordinal}")
    val orderNetstone = createOrderNetstone("CB-TERM-${terminal.ordinal}", orderCodig, terminal)

    assertThatThrownBy {
        orderNetstoneService.changeStatus(
          orderNetstone,
          OrderNetstone.OrderNetstoneStatus.CONFIRMED,
        )
      }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun markReceived_sets_reception_data() {
    val orderCodig = createOrderCodig("CA-OB-08")
    val orderNetstone =
      createOrderNetstone("CB-REC-01", orderCodig, OrderNetstone.OrderNetstoneStatus.IN_PRODUCTION)

    val received =
      orderNetstoneService.markReceived(
        orderNetstone,
        LocalDate.of(2026, 4, 1),
        true,
        "All items conform",
      )

    assertThat(received.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.RECEIVED)
    assertThat(received.receptionDate).isEqualTo(LocalDate.of(2026, 4, 1))
    assertThat(received.receptionConforming).isTrue
    assertThat(received.receptionReserve).isEqualTo("All items conform")
  }

  @Test
  fun markReceived_with_non_conforming_reception() {
    val orderCodig = createOrderCodig("CA-OB-09")
    val orderNetstone =
      createOrderNetstone("CB-REC-02", orderCodig, OrderNetstone.OrderNetstoneStatus.CONFIRMED)

    val received =
      orderNetstoneService.markReceived(
        orderNetstone,
        LocalDate.of(2026, 4, 2),
        false,
        "3 items damaged",
      )

    assertThat(received.receptionConforming).isFalse
    assertThat(received.receptionReserve).isEqualTo("3 items damaged")
  }

  @Test
  fun findByOrderCodigId_returns_linked_orders() {
    val orderCodig = createOrderCodig("CA-OB-10")
    createOrderNetstone("CB-LINK-01", orderCodig, OrderNetstone.OrderNetstoneStatus.SENT)

    val found = orderNetstoneRepository.findByOrderCodigId(orderCodig.id!!)
    assertThat(found).hasSize(1)
    assertThat(found[0].orderNumber).isEqualTo("CB-LINK-01")
  }

  @Test
  fun default_status_is_SENT() {
    val orderCodig = createOrderCodig("CA-OB-11")
    val orderNetstone = OrderNetstone("CB-DEF-01", orderCodig)
    assertThat(orderNetstone.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.SENT)
  }

  @Test
  fun saveWithLines_creates_orderNetstone_with_lines() {
    val orderCodig = createOrderCodig("CA-OB-SWL")
    val orderNetstone = OrderNetstone("", orderCodig)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Component")
    line.quantity = BigDecimal("3")
    line.unitPriceExclTax = BigDecimal("60.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = orderNetstoneService.saveWithLines(orderNetstone, listOf(line))

    assertThat(saved.orderNumber).startsWith("NST_PO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("180.00")

    val lines = orderNetstoneService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Component")
    assertThat(lines[0].position).isEqualTo(0)
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val orderCodig = createOrderCodig("CA-OB-SWL2")
    val orderNetstone = OrderNetstone("OB-SWL-01", orderCodig)

    val line1 = DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Part A")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    orderNetstoneService.saveWithLines(orderNetstone, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Part B")
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    val saved = orderNetstoneService.saveWithLines(orderNetstone, listOf(line2))

    val lines = orderNetstoneService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Part B")
  }
}
