package fr.axl.lvy.order

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class OrderNetstoneServiceTest {

  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var orderNetstoneService: OrderNetstoneService
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var testData: TestDataFactory

  @BeforeEach
  fun ensureNetstoneOwnCompany() {
    if (clientService.findDefaultCodigSupplier().isEmpty) {
      val netstone = Client("CLI-OB-NET", "Netstone")
      netstone.type = Client.ClientType.OWN_COMPANY
      netstone.role = Client.ClientRole.OWN_COMPANY
      netstone.visibleCompany = fr.axl.lvy.user.User.Company.NETSTONE
      clientService.save(netstone)
    }
  }

  private fun createOrderCodig(number: String): OrderCodig {
    val client = clientRepository.save(Client("OB-${number.takeLast(10)}", "Client"))
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
  fun save_defaults_payment_term_and_fiscal_position_from_netstone() {
    val netstone = clientService.findDefaultCodigSupplier().orElseThrow()
    val paymentTerm = paymentTermRepository.saveAndFlush(PaymentTerm("30 jours netstone"))
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Fiscalite Netstone"))
    netstone.paymentTerm = paymentTerm
    netstone.fiscalPosition = fiscalPosition
    clientService.save(netstone)

    val orderCodig = createOrderCodig("CA-OB-DEFAULTS")
    val orderNetstone = OrderNetstone("CB-DEFAULTS-01", orderCodig)

    val saved = orderNetstoneService.save(orderNetstone)

    assertThat(saved.paymentTerm?.id).isEqualTo(paymentTerm.id)
    assertThat(saved.fiscalPosition?.id).isEqualTo(fiscalPosition.id)
  }

  @Test
  fun save_defaults_delivery_location_from_codig_company() {
    val codig = Client("CLI-OB-COD", "Codig")
    codig.type = Client.ClientType.OWN_COMPANY
    codig.role = Client.ClientRole.OWN_COMPANY
    codig.visibleCompany = fr.axl.lvy.user.User.Company.CODIG
    codig.deliveryAddresses.add(
      ClientDeliveryAddress(codig, "Depot Codig", "15 rue du Depot\n75000 Paris").apply {
        defaultAddress = true
      }
    )
    clientService.save(codig)

    val orderCodig = createOrderCodig("CA-OB-DELIV")
    val orderNetstone = OrderNetstone("CB-DELIV-01", orderCodig)

    val saved = orderNetstoneService.save(orderNetstone)

    assertThat(saved.deliveryLocation).isEqualTo("15 rue du Depot\n75000 Paris")
  }

  @Test
  fun findAll_paginated_excludes_soft_deleted() {
    val orderCodigKeep = createOrderCodig("CA-OB-PAGE-KEEP")
    val orderCodigGone = createOrderCodig("CA-OB-PAGE-GONE")
    val kept =
      createOrderNetstone("CB-PAGE-KEEP", orderCodigKeep, OrderNetstone.OrderNetstoneStatus.SENT)
    val gone =
      createOrderNetstone("CB-PAGE-GONE", orderCodigGone, OrderNetstone.OrderNetstoneStatus.SENT)
    orderNetstoneService.delete(gone.id!!)
    orderNetstoneRepository.flush()

    val page = orderNetstoneService.findAll(PageRequest.of(0, 100))
    assertThat(page.content).anyMatch { it.orderNumber == kept.orderNumber }
    assertThat(page.content).noneMatch { it.orderNumber == gone.orderNumber }
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
  fun status_transition_confirmed_to_received() {
    val orderCodig = createOrderCodig("CA-OB-04")
    val orderNetstone =
      createOrderNetstone("CB-ST-02", orderCodig, OrderNetstone.OrderNetstoneStatus.CONFIRMED)

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
      createOrderNetstone("CB-REC-01", orderCodig, OrderNetstone.OrderNetstoneStatus.CONFIRMED)

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
    val supplier =
      clientRepository.save(
        Client("CLI-OB-SUP", "Supplier One").apply { role = Client.ClientRole.PRODUCER }
      )
    val product = testData.createMtoProduct("PRD-OB-SUP")
    product.replaceSuppliers(listOf(supplier))

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Component")
    line.product = product
    line.quantity = BigDecimal("3")
    line.unitPriceExclTax = BigDecimal("60.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = orderNetstoneService.saveWithLines(orderNetstone, listOf(line))

    assertThat(saved.orderNumber).startsWith("NST_PO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("180.00")
    assertThat(saved.supplier?.id).isEqualTo(supplier.id)

    val lines = orderNetstoneService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Component")
    assertThat(lines[0].position).isEqualTo(0)
  }

  @Test
  fun saveWithLines_leaves_supplier_null_when_no_line_has_a_supplier() {
    val orderCodig = createOrderCodig("CA-OB-NO-SUP")
    val orderNetstone = OrderNetstone("", orderCodig)

    val product = testData.createMtoProduct("PRD-OB-NO-SUP")
    // no replaceSuppliers() call — product.suppliers is empty

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Item without supplier")
    line.product = product
    line.quantity = BigDecimal.ONE
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = orderNetstoneService.saveWithLines(orderNetstone, listOf(line))

    assertThat(saved.supplier).isNull()
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
