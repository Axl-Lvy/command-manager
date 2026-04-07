package fr.axl.lvy.sale

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderARepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class SalesAServiceTest {

  @Autowired lateinit var salesAService: SalesAService
  @Autowired lateinit var salesARepository: SalesARepository
  @Autowired lateinit var salesBRepository: SalesBRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createSalesA(
    number: String,
    client: Client,
    status: SalesA.SalesAStatus = SalesA.SalesAStatus.DRAFT,
  ): SalesA {
    val sale = SalesA(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    sale.vatRate = BigDecimal("20.00")
    return salesARepository.save(sale)
  }

  @Test
  fun save_and_retrieve_sale() {
    val client = testData.createClient("CLI-SA01")
    val sale = SalesA("", client, LocalDate.of(2026, 3, 1))
    salesAService.save(sale)

    val found = salesAService.findById(sale.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).startsWith("CoD_SO_")
  }

  @Test
  fun save_uses_client_addresses_when_blank() {
    val client = testData.createClient("CLI-SA02", "123 Billing St", "456 Shipping Ave")
    val sale = SalesA("SA-ADDR-01", client, LocalDate.of(2026, 3, 1))
    val saved = salesAService.save(sale)

    assertThat(saved.billingAddress).isEqualTo("123 Billing St")
    assertThat(saved.shippingAddress).isEqualTo("456 Shipping Ave")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-SA03")
    val sale = createSalesA("SA-DEL-01", client)

    salesAService.delete(sale.id!!)
    salesARepository.flush()

    assertThat(salesAService.findAll()).noneMatch { it.saleNumber == "SA-DEL-01" }
  }

  @Test
  fun syncGeneratedOrder_creates_order_and_lines() {
    val client = testData.createClient("CLI-SA04")
    val sale = createSalesA("SA-SYNC-01", client)
    sale.subject = "Test Subject"
    sale.currency = "USD"
    sale.incoterms = "FOB"
    salesARepository.saveAndFlush(sale)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        sale.id!!,
        "Widget",
        quantity = BigDecimal("5"),
        unitPrice = BigDecimal("100.00"),
      )

    val result = salesAService.syncGeneratedOrder(sale, listOf(line))

    assertThat(result.orderA).isNotNull
    val order = result.orderA!!
    assertThat(order.orderNumber).startsWith("CoD_PO_")
    assertThat(order.client.id).isEqualTo(client.id)
    assertThat(order.subject).isEqualTo("Test Subject")
    assertThat(order.currency).isEqualTo("USD")
    assertThat(order.incoterms).isEqualTo("FOB")

    val orderLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        order.id!!,
      )
    assertThat(orderLines).hasSize(1)
    assertThat(orderLines[0].designation).isEqualTo("Widget")
    assertThat(orderLines[0].quantity).isEqualByComparingTo("5")
    assertThat(orderLines[0].vatRate).isEqualByComparingTo("20.00")
    assertThat(order.totalExclTax).isEqualByComparingTo("500.00")
  }

  @Test
  fun syncGeneratedOrder_updates_existing_order() {
    val client = testData.createClient("CLI-SA05")
    val sale = createSalesA("SA-SYNC-02", client)
    salesARepository.flush()

    val line1 =
      testData.createDocumentLine(DocumentLine.DocumentType.SALES_A, sale.id!!, "Widget A")

    salesAService.syncGeneratedOrder(sale, listOf(line1))
    val firstOrderId = sale.orderA!!.id!!

    val line2 =
      testData.createDocumentLine(DocumentLine.DocumentType.SALES_A, sale.id!!, "Widget B")

    salesAService.syncGeneratedOrder(sale, listOf(line2))

    assertThat(sale.orderA!!.id).isEqualTo(firstOrderId)
  }

  @Test
  fun syncGeneratedOrder_creates_salesB_when_validated_with_mto() {
    val client = testData.createClient("CLI-SA06")
    val sale = createSalesA("SA-MTO-01", client, SalesA.SalesAStatus.VALIDATED)
    salesARepository.flush()

    val mtoProduct = Product("PRD-MTO-SA", "Custom Part")
    mtoProduct.type = Product.ProductType.PRODUCT
    mtoProduct.mto = true
    mtoProduct.sellingPriceExclTax = BigDecimal("100.00")
    mtoProduct.purchasePriceExclTax = BigDecimal("60.00")
    productRepository.saveAndFlush(mtoProduct)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        sale.id!!,
        "Custom Part",
        product = mtoProduct,
      )

    salesAService.syncGeneratedOrder(sale, listOf(line))

    val salesB = salesBRepository.findBySalesAId(sale.id!!)
    assertThat(salesB).isNotNull
    assertThat(salesB!!.status).isEqualTo(SalesB.SalesBStatus.VALIDATED)
  }

  @Test
  fun syncGeneratedOrder_deletes_salesB_when_no_mto_lines() {
    val client = testData.createClient("CLI-SA07")
    val sale = createSalesA("SA-MTO-02", client, SalesA.SalesAStatus.VALIDATED)
    salesARepository.flush()

    val regularProduct = Product("PRD-REG-SA", "Standard Part")
    regularProduct.type = Product.ProductType.PRODUCT
    regularProduct.mto = false
    regularProduct.sellingPriceExclTax = BigDecimal("50.00")
    productRepository.saveAndFlush(regularProduct)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        sale.id!!,
        "Standard Part",
        product = regularProduct,
      )

    salesAService.syncGeneratedOrder(sale, listOf(line))

    val salesB = salesBRepository.findBySalesAId(sale.id!!)
    assertThat(salesB == null || salesB.isDeleted()).isTrue
  }

  @Test
  fun recalculateTotals_sums_lines() {
    val client = testData.createClient("CLI-SA08")
    val sale = createSalesA("SA-CALC-01", client)

    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_A, sale.id!!, "Item 1")
    line1.quantity = BigDecimal("2")
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    line1.recalculate()

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_A, sale.id!!, "Item 2")
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
}
