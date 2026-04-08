package fr.axl.lvy.order

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesARepository
import fr.axl.lvy.sale.SalesBRepository
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
  @Autowired lateinit var salesARepository: SalesARepository
  @Autowired lateinit var salesBRepository: SalesBRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createOrderA(number: String, client: Client, status: OrderA.OrderAStatus): OrderA {
    val order = OrderA(number, client, LocalDate.of(2026, 3, 1))
    order.status = status
    return orderARepository.save(order)
  }

  @Test
  fun save_and_retrieve_order() {
    val client = testData.createClient("CLI-OA01")
    val order = OrderA("CA-2026-0001", client, LocalDate.of(2026, 3, 1))
    order.subject = "Test Order"
    orderAService.save(order)

    val found = orderAService.findById(order.id!!)
    assertThat(found).isPresent
    assertThat(found.get().orderNumber).isEqualTo("CA-2026-0001")
  }

  @Test
  fun default_status_is_draft() {
    val client = testData.createClient("CLI-OA00")
    val order = OrderA("CA-DRAFT-001", client, LocalDate.of(2026, 3, 1))
    assertThat(order.status).isEqualTo(OrderA.OrderAStatus.DRAFT)
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-OA02")
    val order = createOrderA("CA-DEL-001", client, OrderA.OrderAStatus.CONFIRMED)

    orderAService.delete(order.id!!)
    orderARepository.flush()

    assertThat(orderAService.findAll()).noneMatch { it.orderNumber == "CA-DEL-001" }
  }

  @Test
  fun isEditable_for_confirmed_in_production_ready() {
    val client = testData.createClient("CLI-OA03")

    assertThat(createOrderA("CA-E1", client, OrderA.OrderAStatus.CONFIRMED).isEditable()).isTrue
    assertThat(createOrderA("CA-E2", client, OrderA.OrderAStatus.IN_PRODUCTION).isEditable()).isTrue
    assertThat(createOrderA("CA-E3", client, OrderA.OrderAStatus.READY).isEditable()).isTrue
    assertThat(createOrderA("CA-E4", client, OrderA.OrderAStatus.DELIVERED).isEditable()).isFalse
    assertThat(createOrderA("CA-E5", client, OrderA.OrderAStatus.INVOICED).isEditable()).isFalse
    assertThat(createOrderA("CA-E6", client, OrderA.OrderAStatus.CANCELLED).isEditable()).isFalse
  }

  @Test
  fun status_transition_confirmed_to_in_production() {
    val client = testData.createClient("CLI-OA04")
    val order = createOrderA("CA-ST-01", client, OrderA.OrderAStatus.CONFIRMED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.IN_PRODUCTION)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.IN_PRODUCTION)
  }

  @Test
  fun status_transition_draft_to_confirmed_creates_salesB_for_mto_lines_using_purchase_price() {
    val client = testData.createClient("CLI-OA-DRAFT-CF")
    val order = createOrderA("CA-ST-DRAFT", client, OrderA.OrderAStatus.DRAFT)
    val salesA = SalesA("SA-LINK-01", client, LocalDate.of(2026, 3, 1))
    salesA.orderA = order
    salesARepository.saveAndFlush(salesA)

    val mtoProduct = Product("PRD-MTO-CF", "Custom Part")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val mtoLine = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Custom Part")
    mtoLine.product = mtoProduct
    mtoLine.quantity = BigDecimal("3")
    mtoLine.unitPriceExclTax = BigDecimal("75.00")
    mtoLine.discountPercent = BigDecimal.ZERO
    mtoLine.vatRate = BigDecimal("20.00")
    mtoLine.position = 0
    mtoLine.recalculate()
    documentLineRepository.saveAndFlush(mtoLine)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CONFIRMED)

    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.CONFIRMED)
    val salesB = salesBRepository.findBySalesAId(salesA.id!!)
    assertThat(salesB).isNotNull
    assertThat(salesB!!.status).isEqualTo(fr.axl.lvy.sale.SalesB.SalesBStatus.DRAFT)
    val salesBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        salesB.id!!,
      )
    assertThat(salesBLines).hasSize(1)
    assertThat(salesBLines[0].unitPriceExclTax).isEqualByComparingTo("75.00")
  }

  @Test
  fun status_transition_draft_to_confirmed_deletes_salesB_when_no_mto_lines() {
    val client = testData.createClient("CLI-OA-DRAFT-NO-MTO")
    val order = createOrderA("CA-ST-DRAFT-NM", client, OrderA.OrderAStatus.DRAFT)
    val salesA = SalesA("SA-LINK-NM", client, LocalDate.of(2026, 3, 1))
    salesA.orderA = order
    salesARepository.saveAndFlush(salesA)

    val regularProduct = Product("PRD-REG-CF", "Regular Part")
    regularProduct.type = Product.ProductType.PRODUCT
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("80.00")
    productRepository.saveAndFlush(regularProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, order.id!!, "Regular Part")
    line.product = regularProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("80.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CONFIRMED)

    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.CONFIRMED)
    val salesB = salesBRepository.findBySalesAId(salesA.id!!)
    assertThat(salesB).isNull()
  }

  @Test
  fun status_transition_draft_to_cancelled_deletes_salesB() {
    val client = testData.createClient("CLI-OA-DRAFT-CAN")
    val order = createOrderA("CA-ST-DRAFT-CN", client, OrderA.OrderAStatus.DRAFT)
    val salesA = SalesA("SA-LINK-CN", client, LocalDate.of(2026, 3, 1))
    salesA.orderA = order
    salesARepository.saveAndFlush(salesA)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CANCELLED)

    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.CANCELLED)
  }

  @Test
  fun saveWithLines_with_confirmed_status_creates_salesB_for_mto() {
    val client = testData.createClient("CLI-OA-SWL-CF")
    val order = OrderA("", client, LocalDate.of(2026, 3, 1))
    order.status = OrderA.OrderAStatus.CONFIRMED
    order.vatRate = BigDecimal("20.00")
    val savedOrder = orderAService.save(order)

    val salesA = SalesA("SA-LINK-SWL", client, LocalDate.of(2026, 3, 1))
    salesA.orderA = savedOrder
    salesARepository.saveAndFlush(salesA)

    val mtoProduct = Product("PRD-MTO-SWL", "MTO Widget")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 0L, "MTO Widget")
    line.product = mtoProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("60.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderAService.saveWithLines(savedOrder, listOf(line))

    assertThat(saved.purchasePriceExclTax).isEqualByComparingTo("120.00")
    val salesB = salesBRepository.findBySalesAId(salesA.id!!)
    assertThat(salesB).isNotNull
  }

  @Test
  fun saveWithLines_with_confirmed_status_deletes_salesB_when_no_mto() {
    val client = testData.createClient("CLI-OA-SWL-NM")
    val order = OrderA("", client, LocalDate.of(2026, 3, 1))
    order.status = OrderA.OrderAStatus.CONFIRMED
    order.vatRate = BigDecimal("20.00")
    val savedOrder = orderAService.save(order)

    val salesA = SalesA("SA-LINK-NM2", client, LocalDate.of(2026, 3, 1))
    salesA.orderA = savedOrder
    salesARepository.saveAndFlush(salesA)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 0L, "Regular Widget")
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("50.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderAService.saveWithLines(savedOrder, listOf(line))

    assertThat(saved.totalExclTax).isEqualByComparingTo("100.00")
    val salesB = salesBRepository.findBySalesAId(salesA.id!!)
    assertThat(salesB).isNull()
  }

  @Test
  fun status_transition_in_production_to_ready() {
    val client = testData.createClient("CLI-OA05")
    val order = createOrderA("CA-ST-02", client, OrderA.OrderAStatus.IN_PRODUCTION)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.READY)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.READY)
  }

  @Test
  fun status_transition_ready_to_delivered() {
    val client = testData.createClient("CLI-OA06")
    val order = createOrderA("CA-ST-03", client, OrderA.OrderAStatus.READY)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.DELIVERED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.DELIVERED)
  }

  @Test
  fun status_transition_delivered_to_invoiced() {
    val client = testData.createClient("CLI-OA07")
    val order = createOrderA("CA-ST-04", client, OrderA.OrderAStatus.DELIVERED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.INVOICED)
  }

  @Test
  fun status_transition_confirmed_to_cancelled() {
    val client = testData.createClient("CLI-OA08")
    val order = createOrderA("CA-ST-05", client, OrderA.OrderAStatus.CONFIRMED)

    val updated = orderAService.changeStatus(order, OrderA.OrderAStatus.CANCELLED)
    assertThat(updated.status).isEqualTo(OrderA.OrderAStatus.CANCELLED)
  }

  @Test
  fun invalid_status_transition_throws() {
    val client = testData.createClient("CLI-OA09")
    val order = createOrderA("CA-ST-06", client, OrderA.OrderAStatus.CONFIRMED)

    assertThatThrownBy { orderAService.changeStatus(order, OrderA.OrderAStatus.INVOICED) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @ParameterizedTest
  @EnumSource(value = OrderA.OrderAStatus::class, names = ["INVOICED", "CANCELLED"])
  fun no_transitions_from_terminal_statuses(terminal: OrderA.OrderAStatus) {
    val client = testData.createClient("CLI-OA-T${terminal.ordinal}")
    val order = createOrderA("CA-TERM-${terminal.ordinal}", client, terminal)

    assertThatThrownBy { orderAService.changeStatus(order, OrderA.OrderAStatus.CONFIRMED) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun recalculateTotals_includes_margin() {
    val client = testData.createClient("CLI-OA10")
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
    val client = testData.createClient("CLI-OA11")
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
    assertThat(copy.status).isEqualTo(OrderA.OrderAStatus.DRAFT)

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
    val client = testData.createClient("CLI-OA12")
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
    val client = testData.createClient("CLI-OA13")
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

  @Test
  fun saveWithLines_creates_order_with_lines() {
    val client = testData.createClient("CLI-OA-SWL")
    val order = OrderA("", client, LocalDate.of(2026, 3, 1))
    order.vatRate = BigDecimal("20.00")

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 0L, "Widget")
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderAService.saveWithLines(order, listOf(line))

    assertThat(saved.orderNumber).startsWith("CoD_PO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("500.00")
    assertThat(saved.totalVat).isEqualByComparingTo("100.00")
    assertThat(saved.totalInclTax).isEqualByComparingTo("600.00")

    val lines = orderAService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Widget")
    assertThat(lines[0].vatRate).isEqualByComparingTo("20.00")
    assertThat(lines[0].position).isEqualTo(0)
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val client = testData.createClient("CLI-OA-SWL2")
    val order = OrderA("OA-SWL-01", client, LocalDate.of(2026, 3, 1))
    order.vatRate = BigDecimal("20.00")

    val line1 = DocumentLine(DocumentLine.DocumentType.ORDER_A, 0L, "Widget A")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    orderAService.saveWithLines(order, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.ORDER_A, 0L, "Widget B")
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    val saved = orderAService.saveWithLines(order, listOf(line2))

    val lines = orderAService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Widget B")
  }
}
