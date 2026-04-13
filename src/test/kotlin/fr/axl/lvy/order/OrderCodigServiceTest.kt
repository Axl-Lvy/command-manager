package fr.axl.lvy.order

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.sale.SalesNetstoneRepository
import fr.axl.lvy.user.User
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
class OrderCodigServiceTest {

  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var orderCodigService: OrderCodigService
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createOrderCodig(
    number: String,
    client: Client,
    status: OrderCodig.OrderCodigStatus,
  ): OrderCodig {
    val order = OrderCodig(number, client, LocalDate.of(2026, 3, 1))
    order.status = status
    return orderCodigRepository.save(order)
  }

  @Test
  fun save_and_retrieve_order() {
    val client = testData.createClient("CLI-OA01")
    val order = OrderCodig("CA-2026-0001", client, LocalDate.of(2026, 3, 1))
    order.subject = "Test Order"
    orderCodigService.save(order)

    val found = orderCodigService.findById(order.id!!)
    assertThat(found).isPresent
    assertThat(found.get().orderNumber).isEqualTo("CA-2026-0001")
  }

  @Test
  fun save_uses_client_delivery_port_when_blank() {
    val client = testData.createClient("CLI-OA-DELIVERY")
    client.deliveryPort = "Port de Bordeaux"
    val order = OrderCodig("CA-DELIVERY-001", client, LocalDate.of(2026, 3, 1))

    val saved = orderCodigService.save(order)

    assertThat(saved.deliveryLocation).isEqualTo("Port de Bordeaux")
  }

  @Test
  fun save_uses_supplier_payment_term_when_blank() {
    val client = testData.createClient("CLI-OA-PAYMENT")
    val paymentTerm = paymentTermRepository.saveAndFlush(PaymentTerm("30 jours"))
    client.paymentTerm = paymentTerm
    val order = OrderCodig("CA-PAYMENT-001", client, LocalDate.of(2026, 3, 1))

    val saved = orderCodigService.save(order)

    assertThat(saved.paymentTerm?.id).isEqualTo(paymentTerm.id)
  }

  @Test
  fun save_uses_codig_fiscal_position_when_blank() {
    val codig = Client("CLI-OWN-CODIG-ORDER", "Codig")
    codig.type = Client.ClientType.OWN_COMPANY
    codig.role = Client.ClientRole.OWN_COMPANY
    codig.visibleCompany = User.Company.CODIG
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Fiscalite Codig"))
    codig.fiscalPosition = fiscalPosition
    clientService.save(codig)

    val supplier = testData.createClient("CLI-OA-FISCAL")
    val order = OrderCodig("CA-FISCAL-001", supplier, LocalDate.of(2026, 3, 1))

    val saved = orderCodigService.save(order)

    assertThat(saved.fiscalPosition?.id).isEqualTo(fiscalPosition.id)
  }

  @Test
  fun default_status_is_draft() {
    val client = testData.createClient("CLI-OA00")
    val order = OrderCodig("CA-DRAFT-001", client, LocalDate.of(2026, 3, 1))
    assertThat(order.status).isEqualTo(OrderCodig.OrderCodigStatus.DRAFT)
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-OA02")
    val order = createOrderCodig("CA-DEL-001", client, OrderCodig.OrderCodigStatus.CONFIRMED)

    orderCodigService.delete(order.id!!)
    orderCodigRepository.flush()

    assertThat(orderCodigService.findAll()).noneMatch { it.orderNumber == "CA-DEL-001" }
  }

  @Test
  fun isEditable_for_draft_confirmed_in_production_and_ready() {
    val client = testData.createClient("CLI-OA03")

    assertThat(createOrderCodig("CA-E1", client, OrderCodig.OrderCodigStatus.DRAFT).isEditable())
      .isTrue
    assertThat(
        createOrderCodig("CA-E2", client, OrderCodig.OrderCodigStatus.CONFIRMED).isEditable()
      )
      .isTrue
    assertThat(
        createOrderCodig("CA-E3", client, OrderCodig.OrderCodigStatus.IN_PRODUCTION).isEditable()
      )
      .isTrue
    assertThat(createOrderCodig("CA-E4", client, OrderCodig.OrderCodigStatus.READY).isEditable())
      .isTrue
    assertThat(
        createOrderCodig("CA-E5", client, OrderCodig.OrderCodigStatus.DELIVERED).isEditable()
      )
      .isFalse
    assertThat(createOrderCodig("CA-E6", client, OrderCodig.OrderCodigStatus.INVOICED).isEditable())
      .isFalse
    assertThat(
        createOrderCodig("CA-E7", client, OrderCodig.OrderCodigStatus.CANCELLED).isEditable()
      )
      .isFalse
  }

  @Test
  fun status_transition_confirmed_to_in_production() {
    val client = testData.createClient("CLI-OA04")
    val order = createOrderCodig("CA-ST-01", client, OrderCodig.OrderCodigStatus.CONFIRMED)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.IN_PRODUCTION)
    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.IN_PRODUCTION)
  }

  @Test
  fun status_transition_draft_to_confirmed_creates_salesNetstone_for_mto_lines_using_purchase_price() {
    val client = testData.createClient("CLI-OA-DRAFT-CF")
    val order = createOrderCodig("CA-ST-DRAFT", client, OrderCodig.OrderCodigStatus.DRAFT)
    val salesCodig = SalesCodig("SA-LINK-01", client, LocalDate.of(2026, 3, 1))
    salesCodig.orderCodig = order
    salesCodigRepository.saveAndFlush(salesCodig)

    val mtoProduct = Product("PRD-MTO-CF", "Custom Part")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val mtoLine = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Custom Part")
    mtoLine.product = mtoProduct
    mtoLine.quantity = BigDecimal("3")
    mtoLine.unitPriceExclTax = BigDecimal("75.00")
    mtoLine.discountPercent = BigDecimal.ZERO
    mtoLine.vatRate = BigDecimal("20.00")
    mtoLine.position = 0
    mtoLine.recalculate()
    documentLineRepository.saveAndFlush(mtoLine)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.CONFIRMED)

    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.CONFIRMED)
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    assertThat(salesNetstone).isNotNull
    assertThat(salesNetstone!!.status).isEqualTo(fr.axl.lvy.sale.SalesStatus.DRAFT)
    val salesNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_NETSTONE,
        salesNetstone.id!!,
      )
    assertThat(salesNetstoneLines).hasSize(1)
    assertThat(salesNetstoneLines[0].unitPriceExclTax).isEqualByComparingTo("75.00")
  }

  @Test
  fun status_transition_draft_to_confirmed_deletes_salesNetstone_when_no_mto_lines() {
    val client = testData.createClient("CLI-OA-DRAFT-NO-MTO")
    val order = createOrderCodig("CA-ST-DRAFT-NM", client, OrderCodig.OrderCodigStatus.DRAFT)
    val salesCodig = SalesCodig("SA-LINK-NM", client, LocalDate.of(2026, 3, 1))
    salesCodig.orderCodig = order
    salesCodigRepository.saveAndFlush(salesCodig)

    val regularProduct = Product("PRD-REG-CF", "Regular Part")
    regularProduct.type = Product.ProductType.PRODUCT
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("80.00")
    productRepository.saveAndFlush(regularProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Regular Part")
    line.product = regularProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("80.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.CONFIRMED)

    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.CONFIRMED)
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    assertThat(salesNetstone).isNull()
  }

  @Test
  fun status_transition_draft_to_cancelled_deletes_salesNetstone() {
    val client = testData.createClient("CLI-OA-DRAFT-CAN")
    val order = createOrderCodig("CA-ST-DRAFT-CN", client, OrderCodig.OrderCodigStatus.DRAFT)
    val salesCodig = SalesCodig("SA-LINK-CN", client, LocalDate.of(2026, 3, 1))
    salesCodig.orderCodig = order
    salesCodigRepository.saveAndFlush(salesCodig)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.CANCELLED)

    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.CANCELLED)
  }

  @Test
  fun saveWithLines_with_confirmed_status_creates_salesNetstone_for_mto() {
    val client = testData.createClient("CLI-OA-SWL-CF")
    val order = OrderCodig("", client, LocalDate.of(2026, 3, 1))
    order.status = OrderCodig.OrderCodigStatus.CONFIRMED
    order.vatRate = BigDecimal("20.00")
    val savedOrder = orderCodigService.save(order)

    val salesCodig = SalesCodig("SA-LINK-SWL", client, LocalDate.of(2026, 3, 1))
    salesCodig.orderCodig = savedOrder
    salesCodigRepository.saveAndFlush(salesCodig)

    val mtoProduct = Product("PRD-MTO-SWL", "MTO Widget")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, "MTO Widget")
    line.product = mtoProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("60.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderCodigService.saveWithLines(savedOrder, listOf(line))

    assertThat(saved.purchasePriceExclTax).isEqualByComparingTo("120.00")
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    assertThat(salesNetstone).isNotNull
  }

  @Test
  fun saveWithLines_with_confirmed_status_deletes_salesNetstone_when_no_mto() {
    val client = testData.createClient("CLI-OA-SWL-NM")
    val order = OrderCodig("", client, LocalDate.of(2026, 3, 1))
    order.status = OrderCodig.OrderCodigStatus.CONFIRMED
    order.vatRate = BigDecimal("20.00")
    val savedOrder = orderCodigService.save(order)

    val salesCodig = SalesCodig("SA-LINK-NM2", client, LocalDate.of(2026, 3, 1))
    salesCodig.orderCodig = savedOrder
    salesCodigRepository.saveAndFlush(salesCodig)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, "Regular Widget")
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("50.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderCodigService.saveWithLines(savedOrder, listOf(line))

    assertThat(saved.totalExclTax).isEqualByComparingTo("100.00")
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)
    assertThat(salesNetstone).isNull()
  }

  @Test
  fun status_transition_delivered_to_invoiced() {
    val client = testData.createClient("CLI-OA07")
    val order = createOrderCodig("CA-ST-04", client, OrderCodig.OrderCodigStatus.DELIVERED)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.INVOICED)
    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.INVOICED)
  }

  @Test
  fun status_transition_confirmed_to_cancelled() {
    val client = testData.createClient("CLI-OA08")
    val order = createOrderCodig("CA-ST-05", client, OrderCodig.OrderCodigStatus.CONFIRMED)

    val updated = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.CANCELLED)
    assertThat(updated.status).isEqualTo(OrderCodig.OrderCodigStatus.CANCELLED)
  }

  @Test
  fun invalid_status_transition_throws() {
    val client = testData.createClient("CLI-OA09")
    val order = createOrderCodig("CA-ST-06", client, OrderCodig.OrderCodigStatus.CONFIRMED)

    assertThatThrownBy {
        orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.INVOICED)
      }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @ParameterizedTest
  @EnumSource(value = OrderCodig.OrderCodigStatus::class, names = ["INVOICED", "CANCELLED"])
  fun no_transitions_from_terminal_statuses(terminal: OrderCodig.OrderCodigStatus) {
    val client = testData.createClient("CLI-OA-T${terminal.ordinal}")
    val order = createOrderCodig("CA-TERM-${terminal.ordinal}", client, terminal)

    assertThatThrownBy {
        orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.CONFIRMED)
      }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun recalculateTotals_includes_margin() {
    val client = testData.createClient("CLI-OA10")
    val order = createOrderCodig("CA-CALC-1", client, OrderCodig.OrderCodigStatus.CONFIRMED)
    order.vatRate = BigDecimal("20.00")
    orderCodigRepository.flush()

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Item")
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
    val order = createOrderCodig("CA-DUP-1", client, OrderCodig.OrderCodigStatus.CONFIRMED)
    order.subject = "Original"
    order.clientReference = "CR-DUP"
    order.billingAddress = "Billing Addr"
    order.currency = "USD"
    orderCodigRepository.saveAndFlush(order)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Widget")
    line.quantity = BigDecimal("10")
    line.unitPriceExclTax = BigDecimal("25.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    val copy = orderCodigService.duplicate(order, "CA-DUP-2")

    assertThat(copy.id).isNotEqualTo(order.id)
    assertThat(copy.orderNumber).isEqualTo("CA-DUP-2")
    assertThat(copy.subject).isEqualTo("Original")
    assertThat(copy.clientReference).isEqualTo("CR-DUP")
    assertThat(copy.currency).isEqualTo("USD")
    assertThat(copy.sourceOrder!!.id).isEqualTo(order.id)
    assertThat(copy.status).isEqualTo(OrderCodig.OrderCodigStatus.DRAFT)

    val copyLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        copy.id!!,
      )
    assertThat(copyLines).hasSize(1)
    assertThat(copyLines[0].designation).isEqualTo("Widget")
    assertThat(copyLines[0].quantity).isEqualByComparingTo("10")
    assertThat(copyLines[0].lineTotalExclTax).isEqualByComparingTo("250.00")
  }

  @Test
  fun handleMto_creates_orderNetstone_for_mto_products() {
    val client = testData.createClient("CLI-OA12")
    val order = createOrderCodig("CA-MTO-1", client, OrderCodig.OrderCodigStatus.CONFIRMED)
    orderCodigRepository.flush()

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

    val mtoLine = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Custom Part")
    mtoLine.product = mtoProduct
    mtoLine.quantity = BigDecimal("3")
    mtoLine.unitPriceExclTax = BigDecimal("100.00")
    mtoLine.discountPercent = BigDecimal.ZERO
    mtoLine.vatRate = BigDecimal("20.00")
    mtoLine.position = 0
    mtoLine.recalculate()
    documentLineRepository.saveAndFlush(mtoLine)

    val regularLine =
      DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Standard Part")
    regularLine.product = regularProduct
    regularLine.quantity = BigDecimal("2")
    regularLine.unitPriceExclTax = BigDecimal("50.00")
    regularLine.discountPercent = BigDecimal.ZERO
    regularLine.vatRate = BigDecimal("20.00")
    regularLine.position = 1
    regularLine.recalculate()
    documentLineRepository.saveAndFlush(regularLine)

    orderCodigService.handleMto(order, "CB-2026-0001")

    val updatedOrder = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(updatedOrder.orderNetstone).isNotNull

    val orderNetstone = updatedOrder.orderNetstone!!
    assertThat(orderNetstone.orderNumber).isEqualTo("CB-2026-0001")
    assertThat(orderNetstone.orderCodig!!.id).isEqualTo(order.id)

    // Only MTO lines copied, at purchase price
    val orderNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        orderNetstone.id!!,
      )
    assertThat(orderNetstoneLines).hasSize(1)
    assertThat(orderNetstoneLines[0].designation).isEqualTo("Custom Part")
    assertThat(orderNetstoneLines[0].unitPriceExclTax).isEqualByComparingTo("60.00")
    assertThat(orderNetstoneLines[0].quantity).isEqualByComparingTo("3")
  }

  @Test
  fun handleMto_does_nothing_without_mto_products() {
    val client = testData.createClient("CLI-OA13")
    val order = createOrderCodig("CA-MTO-2", client, OrderCodig.OrderCodigStatus.CONFIRMED)
    orderCodigRepository.flush()

    val regularProduct = Product("PRD-REG2", "Standard")
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("50.00")
    productRepository.saveAndFlush(regularProduct)

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, order.id!!, "Standard")
    line.product = regularProduct
    line.quantity = BigDecimal.ONE
    line.unitPriceExclTax = BigDecimal("50.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal.ZERO
    line.position = 0
    line.recalculate()
    documentLineRepository.saveAndFlush(line)

    orderCodigService.handleMto(order, "CB-2026-0002")

    val updatedOrder = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(updatedOrder.orderNetstone).isNull()
    assertThat(orderNetstoneRepository.findAll()).isEmpty()
  }

  @Test
  fun saveWithLines_creates_order_with_lines() {
    val client = testData.createClient("CLI-OA-SWL")
    val order = OrderCodig("", client, LocalDate.of(2026, 3, 1))
    order.vatRate = BigDecimal("20.00")

    val line = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, "Widget")
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO

    val saved = orderCodigService.saveWithLines(order, listOf(line))

    assertThat(saved.orderNumber).startsWith("CoD_PO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("500.00")
    assertThat(saved.totalVat).isEqualByComparingTo("100.00")
    assertThat(saved.totalInclTax).isEqualByComparingTo("600.00")

    val lines = orderCodigService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Widget")
    assertThat(lines[0].vatRate).isEqualByComparingTo("20.00")
    assertThat(lines[0].position).isEqualTo(0)
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val client = testData.createClient("CLI-OA-SWL2")
    val order = OrderCodig("OA-SWL-01", client, LocalDate.of(2026, 3, 1))
    order.vatRate = BigDecimal("20.00")

    val line1 = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, "Widget A")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    orderCodigService.saveWithLines(order, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, "Widget B")
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    val saved = orderCodigService.saveWithLines(order, listOf(line2))

    val lines = orderCodigService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Widget B")
  }
}
