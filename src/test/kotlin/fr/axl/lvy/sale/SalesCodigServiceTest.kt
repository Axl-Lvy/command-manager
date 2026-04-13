package fr.axl.lvy.sale

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.user.User
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class SalesCodigServiceTest {

  @Autowired lateinit var salesCodigService: SalesCodigService
  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var testData: TestDataFactory

  @BeforeEach
  fun ensureOwnCompanies() {
    if (clientService.findDefaultCodigSupplier().isEmpty) {
      val supplier = Client("CLI-SA-OWN-B", "Netstone")
      supplier.type = Client.ClientType.OWN_COMPANY
      supplier.role = Client.ClientRole.OWN_COMPANY
      supplier.visibleCompany = User.Company.NETSTONE
      clientService.save(supplier)
    }
    if (clientService.findDefaultCodigCompany().isEmpty) {
      val codig = Client("CLI-SA-OWN-A", "Codig")
      codig.type = Client.ClientType.OWN_COMPANY
      codig.role = Client.ClientRole.OWN_COMPANY
      codig.visibleCompany = User.Company.CODIG
      clientService.save(codig)
    }
  }

  private fun createSalesCodig(
    number: String,
    client: Client,
    status: SalesStatus = SalesStatus.DRAFT,
  ): SalesCodig {
    val sale = SalesCodig(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    return salesCodigRepository.save(sale)
  }

  @Test
  fun save_and_retrieve_sale() {
    val client = testData.createClient("CLI-SA01")
    val sale = SalesCodig("", client, LocalDate.of(2026, 3, 1))
    salesCodigService.save(sale)

    val found = salesCodigService.findById(sale.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).startsWith("CoD_SO_")
  }

  @Test
  fun save_uses_client_addresses_when_blank() {
    val client = testData.createClient("CLI-SA02", "123 Billing St", "456 Shipping Ave")
    val sale = SalesCodig("SA-ADDR-01", client, LocalDate.of(2026, 3, 1))
    val saved = salesCodigService.save(sale)

    assertThat(saved.billingAddress).isEqualTo("123 Billing St")
    assertThat(saved.shippingAddress).isEqualTo("456 Shipping Ave")
  }

  @Test
  fun save_persists_payment_term() {
    val client = testData.createClient("CLI-SA02B")
    val paymentTerm = paymentTermRepository.saveAndFlush(PaymentTerm("30 jours"))
    val sale = SalesCodig("SA-PAY-01", client, LocalDate.of(2026, 3, 1))
    sale.paymentTerm = paymentTerm

    val saved = salesCodigService.save(sale)

    assertThat(saved.paymentTerm?.id).isEqualTo(paymentTerm.id)
  }

  @Test
  fun save_uses_client_payment_term_when_blank() {
    val client = testData.createClient("CLI-SA02C")
    val paymentTerm = paymentTermRepository.saveAndFlush(PaymentTerm("45 jours"))
    client.paymentTerm = paymentTerm
    val sale = SalesCodig("SA-PAY-02", client, LocalDate.of(2026, 3, 1))

    val saved = salesCodigService.save(sale)

    assertThat(saved.paymentTerm?.id).isEqualTo(paymentTerm.id)
  }

  @Test
  fun save_uses_client_fiscal_position_when_blank() {
    val client = testData.createClient("CLI-SA-FISCAL")
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Intra client"))
    client.fiscalPosition = fiscalPosition
    val sale = SalesCodig("SA-FISCAL-01", client, LocalDate.of(2026, 3, 1))

    val saved = salesCodigService.save(sale)

    assertThat(saved.fiscalPosition?.id).isEqualTo(fiscalPosition.id)
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-SA03")
    val sale = createSalesCodig("SA-DEL-01", client)

    salesCodigService.delete(sale.id!!)
    salesCodigRepository.flush()

    assertThat(salesCodigService.findAll()).noneMatch { it.saleNumber == "SA-DEL-01" }
  }

  @Test
  fun syncGeneratedOrder_creates_order_and_lines() {
    val client = testData.createClient("CLI-SA04")
    val supplier = clientService.findDefaultCodigSupplier().orElseThrow()
    val sale = createSalesCodig("SA-SYNC-01", client)
    sale.subject = "Test Subject"
    sale.currency = "USD"
    salesCodigRepository.saveAndFlush(sale)

    val mtoProduct = testData.createMtoProduct("PRD-SA-SYNC-01")
    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Widget",
        quantity = BigDecimal("5"),
        unitPrice = BigDecimal("100.00"),
        product = mtoProduct,
      )

    val result = salesCodigService.syncGeneratedOrder(sale, listOf(line))

    assertThat(result.orderCodig).isNotNull
    val order = result.orderCodig!!
    assertThat(order.orderNumber).startsWith("CoD_PO_")
    assertThat(order.client.id).isEqualTo(supplier.id)
    assertThat(order.subject).isEqualTo("Test Subject")
    assertThat(order.currency).isEqualTo("USD")
    // incoterms comes from codigCompany.incoterm (null in this test — no incoterm configured)
    // incotermLocation comes from sale.client.deliveryPort (null in this test — no deliveryPort
    // set)

    val orderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        order.id!!,
      )
    assertThat(orderLines).hasSize(1)
    assertThat(orderLines[0].designation).isEqualTo("Widget")
    assertThat(orderLines[0].quantity).isEqualByComparingTo("5")
    assertThat(orderLines[0].unitPriceExclTax).isEqualByComparingTo("60.00")
    assertThat(orderLines[0].vatRate).isEqualByComparingTo("20.00")
    assertThat(order.totalExclTax).isEqualByComparingTo("300.00")
  }

  @Test
  fun syncGeneratedOrder_uses_default_codig_supplier() {
    val client = testData.createClient("CLI-SA-SUPPLIER")
    client.deliveryPort = "Port client"
    val supplier = clientService.findDefaultCodigSupplier().orElseThrow()
    val codigFiscalPosition = fiscalPositionService.save(FiscalPosition("Codig export"))
    val codigCompany = clientService.findDefaultCodigCompany().orElseThrow()
    codigCompany.fiscalPosition = codigFiscalPosition
    clientService.save(codigCompany)

    val sale = createSalesCodig("SA-SUPPLIER-01", client)
    val mtoProduct = testData.createMtoProduct("PRD-SA-SUPPLIER")
    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Widget",
        product = mtoProduct,
      )

    val result = salesCodigService.syncGeneratedOrder(sale, listOf(line))

    assertThat(result.orderCodig).isNotNull
    assertThat(result.orderCodig!!.client.id).isEqualTo(supplier.id)
    assertThat(result.orderCodig!!.fiscalPosition?.position).isEqualTo("Codig export")
    assertThat(result.orderCodig!!.deliveryLocation).isEqualTo("Port client")
  }

  @Test
  fun syncGeneratedOrder_updates_existing_order() {
    val client = testData.createClient("CLI-SA05")
    val sale = createSalesCodig("SA-SYNC-02", client)
    salesCodigRepository.flush()

    val mtoProduct = testData.createMtoProduct("PRD-SA-SYNC-02")

    val line1 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Widget A",
        product = mtoProduct,
      )

    salesCodigService.syncGeneratedOrder(sale, listOf(line1))
    val firstOrderId = sale.orderCodig!!.id!!

    val line2 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Widget B",
        product = mtoProduct,
      )

    salesCodigService.syncGeneratedOrder(sale, listOf(line2))

    assertThat(sale.orderCodig!!.id).isEqualTo(firstOrderId)
  }

  @Test
  fun syncGeneratedOrder_creates_orderCodig_in_draft_without_salesNetstone_when_validated_with_mto() {
    val client = testData.createClient("CLI-SA06")
    val sale = createSalesCodig("SA-MTO-01", client, SalesStatus.VALIDATED)
    salesCodigRepository.flush()

    val mtoProduct = Product("PRD-MTO-SA", "Custom Part")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Custom Part",
        product = mtoProduct,
      )

    salesCodigService.syncGeneratedOrder(sale, listOf(line))

    val savedOrder = orderCodigRepository.findById(sale.orderCodig!!.id!!).orElseThrow()
    assertThat(savedOrder.status).isEqualTo(OrderCodig.OrderCodigStatus.DRAFT)
    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(sale.id!!)
    assertThat(salesNetstone).isNull()
  }

  @Test
  fun syncGeneratedOrder_deletes_salesNetstone_when_no_mto_lines() {
    val client = testData.createClient("CLI-SA07")
    val sale = createSalesCodig("SA-MTO-02", client, SalesStatus.VALIDATED)
    salesCodigRepository.flush()

    val regularProduct = Product("PRD-REG-SA", "Standard Part")
    regularProduct.type = Product.ProductType.PRODUCT
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("50.00")
    productRepository.saveAndFlush(regularProduct)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Standard Part",
        product = regularProduct,
      )

    salesCodigService.syncGeneratedOrder(sale, listOf(line))

    val salesNetstone = salesNetstoneRepository.findBySalesCodigId(sale.id!!)
    assertThat(salesNetstone == null || salesNetstone.isDeleted()).isTrue
    assertThat(sale.orderCodig).isNull()
  }

  @Test
  fun syncGeneratedOrder_does_not_create_orderCodig_for_service_even_if_mto_flag_is_true() {
    val client = testData.createClient("CLI-SA-SVC")
    val sale = createSalesCodig("SA-SVC-01", client, SalesStatus.VALIDATED)
    salesCodigRepository.flush()

    val serviceProduct = Product("SRV-SA-01", "Service")
    serviceProduct.type = Product.ProductType.SERVICE
    serviceProduct.mto = true
    serviceProduct.sellingPriceExclTax = BigDecimal("100.00")
    serviceProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(serviceProduct)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        sale.id!!,
        "Service",
        product = serviceProduct,
      )

    val result = salesCodigService.syncGeneratedOrder(sale, listOf(line))

    assertThat(result.orderCodig).isNull()
    assertThat(salesNetstoneRepository.findBySalesCodigId(sale.id!!)).isNull()
  }

  @Test
  fun recalculateTotals_sums_lines() {
    val client = testData.createClient("CLI-SA08")
    val sale = createSalesCodig("SA-CALC-01", client)

    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, sale.id!!, "Item 1")
    line1.quantity = BigDecimal("2")
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    line1.recalculate()

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, sale.id!!, "Item 2")
    line2.quantity = BigDecimal("3")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    line2.recalculate()

    sale.recalculateTotals(listOf(line1, line2))

    assertThat(sale.totalExclTax).isEqualByComparingTo("350.00")
    assertThat(sale.totalVat).isEqualByComparingTo("70.00")
    assertThat(sale.totalInclTax).isEqualByComparingTo("420.00")
  }

  @Test
  fun saveWithLines_creates_sale_with_lines_and_syncs_order() {
    val client = testData.createClient("CLI-SA-SWL", "123 Billing St", "456 Shipping Ave")
    val sale = SalesCodig("", client, LocalDate.of(2026, 3, 1))

    val mtoProduct = testData.createMtoProduct("PRD-SA-SWL")
    val line = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, 0L, "Widget")
    line.product = mtoProduct
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesCodigService.saveWithLines(sale, listOf(line))

    assertThat(saved.saleNumber).startsWith("CoD_SO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("500.00")
    assertThat(saved.orderCodig).isNotNull

    val lines = salesCodigService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].vatRate).isEqualByComparingTo("20.00")
  }

  @Test
  fun saveWithLines_does_not_create_orderCodig_without_mto_product() {
    val client = testData.createClient("CLI-SA-NO-MTO", "123 Billing St", "456 Shipping Ave")
    val sale = SalesCodig("", client, LocalDate.of(2026, 3, 1))

    val serviceProduct = Product("SRV-SA-02", "Service")
    serviceProduct.type = Product.ProductType.SERVICE
    serviceProduct.mto = true
    serviceProduct.sellingPriceExclTax = BigDecimal("100.00")
    productRepository.saveAndFlush(serviceProduct)

    val line = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, 0L, "Service")
    line.product = serviceProduct
    line.quantity = BigDecimal("1")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesCodigService.saveWithLines(sale, listOf(line))

    assertThat(saved.orderCodig).isNull()
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val client = testData.createClient("CLI-SA-SWL2", "123 Billing St", "456 Shipping Ave")
    val sale = SalesCodig("SA-SWL-01", client, LocalDate.of(2026, 3, 1))

    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, 0L, "Item A")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    salesCodigService.saveWithLines(sale, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_CODIG, 0L, "Item B")
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("5.50")
    val saved = salesCodigService.saveWithLines(sale, listOf(line2))

    val lines = salesCodigService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Item B")
    assertThat(lines[0].vatRate).isEqualByComparingTo("5.50")
  }
}
