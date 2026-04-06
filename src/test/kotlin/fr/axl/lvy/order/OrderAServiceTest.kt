package fr.axl.lvy.order

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
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
class OrderAServiceTest {

  @Autowired lateinit var orderAService: OrderAService
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var orderBRepository: OrderBRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository

  private fun createClient(code: String): Client {
    return clientRepository.save(Client(code, "Client $code"))
  }

  private fun createOrderA(number: String, client: Client, status: OrderA.OrderAStatus): OrderA {
    val order = OrderA(number, client, LocalDate.of(2026, 3, 1))
    order.status = status
    return orderARepository.save(order)
  }

  @Test
  fun save_and_retrieve_order() {
    val client = createClient("CLI-OA01")
    val order = OrderA("CA-2026-0001", client, LocalDate.of(2026, 3, 1))
    order.subject = "Test Order"
    orderAService.save(order)

    val found = orderAService.findById(order.id!!)
    assertThat(found).isPresent
    assertThat(found.get().orderNumber).isEqualTo("CA-2026-0001")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-OA02")
    val order = createOrderA("CA-DEL-001", client, OrderA.OrderAStatus.CONFIRMED)

    orderAService.delete(order.id!!)
    orderARepository.flush()

    assertThat(orderAService.findAll()).noneMatch { it.orderNumber == "CA-DEL-001" }
  }

  @Test
  fun isEditable_for_confirmed_in_production_ready() {
    val client = createClient("CLI-OA03")

    assertThat(createOrderA("CA-E1", client, OrderA.OrderAStatus.CONFIRMED).isEditable()).isTrue
    assertThat(createOrderA("CA-E2", client, OrderA.OrderAStatus.IN_PRODUCTION).isEditable()).isTrue
    assertThat(createOrderA("CA-E3", client, OrderA.OrderAStatus.READY).isEditable()).isTrue
    assertThat(createOrderA("CA-E4", client, OrderA.OrderAStatus.DELIVERED).isEditable()).isFalse
    assertThat(createOrderA("CA-E5", client, OrderA.OrderAStatus.INVOICED).isEditable()).isFalse
    assertThat(createOrderA("CA-E6", client, OrderA.OrderAStatus.CANCELLED).isEditable()).isFalse
  }

  @Test
  fun status_transition_confirmed_to_in_production() {
    val client = createClient("CLI-OA04")
    val order = createOrderA("CA-ST-01", client, OrderA.OrderAStatus.CONFIRMED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.IN_PRODUCTION)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.IN_PRODUCTION)
  }

  @Test
  fun status_transition_in_production_to_ready() {
    val client = createClient("CLI-OA05")
    val order = createOrderA("CA-ST-02", client, OrderA.OrderAStatus.IN_PRODUCTION)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.READY)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.READY)
  }

  @Test
  fun status_transition_ready_to_delivered() {
    val client = createClient("CLI-OA06")
    val order = createOrderA("CA-ST-03", client, OrderA.OrderAStatus.READY)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.DELIVERED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.DELIVERED)
  }

  @Test
  fun status_transition_delivered_to_invoiced() {
    val client = createClient("CLI-OA07")
    val order = createOrderA("CA-ST-04", client, OrderA.OrderAStatus.DELIVERED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.INVOICED)
  }

  @Test
  fun status_transition_confirmed_to_cancelled() {
    val client = createClient("CLI-OA08")
    val order = createOrderA("CA-ST-05", client, OrderA.OrderAStatus.CONFIRMED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CANCELLED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.CANCELLED)
  }

  @Test
  fun invalid_status_transition_throws() {
    val client = createClient("CLI-OA09")
    val order = createOrderA("CA-ST-06", client, OrderA.OrderAStatus.CONFIRMED)

    assertThatThrownBy { orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED) }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @ParameterizedTest
  @EnumSource(value = OrderA.OrderAStatus::class, names = ["INVOICED", "CANCELLED"])
  fun no_transitions_from_terminal_statuses(terminal: OrderA.OrderAStatus) {
    val client = createClient("CLI-OA-T${terminal.ordinal}")
    val order = createOrderA("CA-TERM-${terminal.ordinal}", client, terminal)

    assertThatThrownBy { orderAService.changeStatus(order, OrderA.OrderAStatus.CONFIRMED) }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun recalculateTotals_includes_margin() {
    val client = createClient("CLI-OA10")
    val order = createOrderA("CA-CALC-1", client, OrderA.OrderAStatus.CONFIRMED)
    order.vatRate = BigDecimal("20.00")
    orderARepository.flush()

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Item")
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.recalculate()

    order.recalculateTotals(listOf(line))

    assertThat(order.totalExclTax).isEqualByComparingTo("500.00")
    assertThat(order.totalVat).isEqualByComparingTo("100.00")
    assertThat(order.totalInclTax).isEqualByComparingTo("600.00")
    assertThat(order.marginExclTax).isEqualByComparingTo("0.00")
  }

  @Test
  fun duplicate_copies_order_and_lines() {
    val client = createClient("CLI-OA11")
    val order = createOrderA("CA-DUP-1", client, OrderA.OrderAStatus.CONFIRMED)
    order.subject = "Original"
    order.clientReference = "CR-DUP"
    order.billingAddress = "Billing Addr"
    order.currency = "USD"
    orderARepository.saveAndFlush(order)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Widget")
    line.quantity = BigDecimal("10")
    line.unitPriceExclTax = BigDecimal("25.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    val copy = orderAService.duplicate(order, "CA-DUP-2")

    assertThat(copy.id).isNotEqualTo(order.id)
    assertThat(copy.orderNumber).isEqualTo("CA-DUP-2")
    assertThat(copy.subject).isEqualTo("Original")
    assertThat(copy.clientReference).isEqualTo("CR-DUP")
    assertThat(copy.currency).isEqualTo("USD")
    assertThat(copy.sourceOrder!!.id).isEqualTo(order.id)
    assertThat(copy.status).isEqualTo(OrderA.OrderAStatus.CONFIRMED)

    val copyLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        copy.id!!,
      )
    assertThat(copyLines).hasSize(1)
    assertThat(copyLines[0].designation).isEqualTo("Widget")
    assertThat(copyLines[0].quantity).isEqualByComparingTo("10")
    assertThat(copyLines[0].lineTotalExclTax).isEqualByComparingTo("250.00")
  }

  @Test
  fun handleMto_creates_orderB_for_mto_products() {
    val client = createClient("CLI-OA12")
    val order = createOrderA("CA-MTO-1", client, OrderA.OrderAStatus.CONFIRMED)
    orderARepository.flush()

    val mtoProduct = Product("PRD-MTO", "Custom Part")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val regularProduct = Product("PRD-REG", "Standard Part")
    regularProduct.type = Product.ProductType.PRODUCT
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("50.00")
    regularProduct.purchasePriceExclTax = BigDecimal("30.00")
    productRepository.saveAndFlush(regularProduct)

    val mtoLine = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Custom Part")
    mtoLine.product = mtoProduct
    mtoLine.quantity = BigDecimal("3")
    mtoLine.unitPriceExclTax = BigDecimal("100.00")
    mtoLine.discountPercent = BigDecimal.ZERO
    mtoLine.vatRate = BigDecimal("20.00")
    mtoLine.position = 0
    mtoLine.recalculate()
    documentLineRepository.saveAndFlush(mtoLine)

    val regularLine = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Standard Part")
    regularLine.product = regularProduct
    regularLine.quantity = BigDecimal("2")
    regularLine.unitPriceExclTax = BigDecimal("50.00")
    regularLine.discountPercent = BigDecimal.ZERO
    regularLine.vatRate = BigDecimal("20.00")
    regularLine.position = 1
    regularLine.recalculate()
    documentLineRepository.saveAndFlush(regularLine)

    orderAService.handleMto(order, "CB-2026-0001")

    val updatedOrder = orderARepository.findById(order.id!!).orElseThrow()
    assertThat(updatedOrder.orderB).isNotNull

    val orderB = updatedOrder.orderB!!
    assertThat(orderB.orderNumber).isEqualTo("CB-2026-0001")
    assertThat(orderB.orderA!!.id).isEqualTo(order.id)

    // Only MTO lines copied, at purchase price
    val orderBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_B,
        orderB.id!!,
      )
    assertThat(orderBLines).hasSize(1)
    assertThat(orderBLines[0].designation).isEqualTo("Custom Part")
    assertThat(orderBLines[0].unitPriceExclTax).isEqualByComparingTo("60.00")
    assertThat(orderBLines[0].quantity).isEqualByComparingTo("3")
  }

  @Test
  fun handleMto_does_nothing_without_mto_products() {
    val client = createClient("CLI-OA13")
    val order = createOrderA("CA-MTO-2", client, OrderA.OrderAStatus.CONFIRMED)
    orderARepository.flush()

    val regularProduct = Product("PRD-REG2", "Standard")
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("50.00")
    productRepository.saveAndFlush(regularProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Standard")
    line.product = regularProduct
    line.quantity = BigDecimal.ONE
    line.unitPriceExclTax = BigDecimal("50.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal.ZERO
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    orderAService.handleMto(order, "CB-2026-0002")

    val updatedOrder = orderARepository.findById(order.id!!).orElseThrow()
    assertThat(updatedOrder.orderB).isNull()
    assertThat(orderBRepository.findAll()).isEmpty()
  }
}
