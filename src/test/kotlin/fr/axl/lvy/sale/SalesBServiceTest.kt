package fr.axl.lvy.sale

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.order.OrderBRepository
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
class SalesBServiceTest {

  @Autowired lateinit var salesBService: SalesBService
  @Autowired lateinit var salesAService: SalesAService
  @Autowired lateinit var salesARepository: SalesARepository
  @Autowired lateinit var salesBRepository: SalesBRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderAService: OrderAService
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var orderBRepository: OrderBRepository

  private fun createClient(code: String): Client {
    val client = Client(code, "Client $code")
    client.billingAddress = "123 Billing St"
    client.shippingAddress = "456 Shipping Ave"
    return clientRepository.save(client)
  }

  private fun createSalesAWithOrder(
    number: String,
    client: Client,
    status: SalesA.SalesAStatus = SalesA.SalesAStatus.VALIDATED,
  ): SalesA {
    val sale = SalesA(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    sale.vatRate = BigDecimal("20.00")
    val saved = salesARepository.save(sale)

    val order = OrderA("", client, LocalDate.of(2026, 3, 1))
    val savedOrder = orderAService.save(order)
    saved.orderA = savedOrder
    return salesARepository.saveAndFlush(saved)
  }

  private fun createMtoProduct(ref: String): Product {
    val product = Product(ref, "MTO $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = true
    product.sellingPriceExclTax = BigDecimal("100.00")
    product.purchasePriceExclTax = BigDecimal("60.00")
    return productRepository.saveAndFlush(product)
  }

  private fun createRegularProduct(ref: String): Product {
    val product = Product(ref, "Regular $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = false
    product.sellingPriceExclTax = BigDecimal("50.00")
    product.purchasePriceExclTax = BigDecimal("30.00")
    return productRepository.saveAndFlush(product)
  }

  private fun createDocumentLine(
    type: DocumentLine.DocumentType,
    documentId: Long,
    designation: String,
    product: Product? = null,
    quantity: BigDecimal = BigDecimal.ONE,
    unitPrice: BigDecimal = BigDecimal("100.00"),
  ): DocumentLine {
    val line = DocumentLine(type, documentId, designation)
    line.product = product
    line.quantity = quantity
    line.unitPriceExclTax = unitPrice
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")
    line.position = 0
    line.recalculate()
    return documentLineRepository.saveAndFlush(line)
  }

  @Test
  fun save_and_retrieve_sale() {
    val client = createClient("CLI-SB01")
    val salesA = createSalesAWithOrder("SA-SB-01", client)
    val salesB = SalesB("", salesA)
    salesBService.save(salesB)

    val found = salesBService.findById(salesB.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).startsWith("NST_SO_")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-SB02")
    val salesA = createSalesAWithOrder("SA-SB-02", client)
    val salesB = SalesB("SB-DEL-01", salesA)
    salesBService.save(salesB)

    salesBService.delete(salesB.id!!)
    salesBRepository.flush()

    assertThat(salesBService.findAll()).noneMatch { it.saleNumber == "SB-DEL-01" }
  }

  @Test
  fun createOrUpdateFromValidatedSalesA_creates_salesB_with_mto_lines() {
    val client = createClient("CLI-SB03")
    val salesA = createSalesAWithOrder("SA-SB-03", client)

    val mtoProduct = createMtoProduct("PRD-MTO-SB1")
    val regularProduct = createRegularProduct("PRD-REG-SB1")

    val mtoLine =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item",
        product = mtoProduct,
      )
    val regularLine =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "Regular Item",
        product = regularProduct,
      )

    val result =
      salesBService.createOrUpdateFromValidatedSalesA(salesA, listOf(mtoLine, regularLine))

    assertThat(result.status).isEqualTo(SalesB.SalesBStatus.VALIDATED)

    val salesBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        result.id!!,
      )
    assertThat(salesBLines).hasSize(1)
    assertThat(salesBLines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun createOrUpdateFromValidatedSalesA_updates_existing_salesB() {
    val client = createClient("CLI-SB04")
    val salesA = createSalesAWithOrder("SA-SB-04", client)

    val mtoProduct = createMtoProduct("PRD-MTO-SB2")
    val line =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    val first = salesBService.createOrUpdateFromValidatedSalesA(salesA, listOf(line))
    val firstId = first.id!!

    val line2 =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item Updated",
        product = mtoProduct,
        unitPrice = BigDecimal("200.00"),
      )

    val second = salesBService.createOrUpdateFromValidatedSalesA(salesA, listOf(line2))

    assertThat(second.id).isEqualTo(firstId)
  }

  @Test
  fun syncGeneratedOrder_creates_orderB() {
    val client = createClient("CLI-SB05")
    val salesA = createSalesAWithOrder("SA-SB-05", client)

    val mtoProduct = createMtoProduct("PRD-MTO-SB3")
    val salesB = SalesB("SB-SYNC-01", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_B,
        savedSalesB.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    val result = salesBService.syncGeneratedOrder(savedSalesB, listOf(line))

    assertThat(result.orderB).isNotNull
    val orderB = result.orderB!!
    assertThat(orderB.orderNumber).startsWith("NST_PO_")

    val orderBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_B,
        orderB.id!!,
      )
    assertThat(orderBLines).hasSize(1)
    assertThat(orderBLines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun syncGeneratedOrder_updates_existing_orderB() {
    val client = createClient("CLI-SB06")
    val salesA = createSalesAWithOrder("SA-SB-06", client)

    val mtoProduct = createMtoProduct("PRD-MTO-SB4")
    val salesB = SalesB("SB-SYNC-02", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line1 =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_B,
        savedSalesB.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    salesBService.syncGeneratedOrder(savedSalesB, listOf(line1))
    val firstOrderBId = savedSalesB.orderB!!.id!!

    val line2 =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_B,
        savedSalesB.id!!,
        "MTO Item v2",
        product = mtoProduct,
      )

    salesBService.syncGeneratedOrder(savedSalesB, listOf(line2))

    assertThat(savedSalesB.orderB!!.id).isEqualTo(firstOrderBId)
  }

  @Test
  fun deleteBySalesAId_soft_deletes_salesB_and_orderB() {
    val client = createClient("CLI-SB07")
    val salesA = createSalesAWithOrder("SA-SB-07", client)

    val mtoProduct = createMtoProduct("PRD-MTO-SB5")
    val salesB = SalesB("SB-DEL-02", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line =
      createDocumentLine(
        DocumentLine.DocumentType.SALES_B,
        savedSalesB.id!!,
        "MTO Item",
        product = mtoProduct,
      )
    salesBService.syncGeneratedOrder(savedSalesB, listOf(line))
    salesBRepository.flush()

    val orderBId = savedSalesB.orderB!!.id!!

    salesBService.deleteBySalesAId(salesA.id!!)
    salesBRepository.flush()

    val deletedSalesB = salesBRepository.findById(savedSalesB.id!!).orElseThrow()
    assertThat(deletedSalesB.isDeleted()).isTrue

    val deletedOrderB = orderBRepository.findById(orderBId).orElseThrow()
    assertThat(deletedOrderB.isDeleted()).isTrue
  }

  @Test
  fun recalculateTotals_sums_lines() {
    val client = createClient("CLI-SB08")
    val salesA = createSalesAWithOrder("SA-SB-08", client)
    val salesB = SalesB("SB-CALC-01", salesA)
    salesBService.save(salesB)

    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_B, salesB.id!!, "Item 1")
    line1.quantity = BigDecimal("2")
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    line1.recalculate()

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_B, salesB.id!!, "Item 2")
    line2.quantity = BigDecimal("3")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    line2.recalculate()

    salesB.recalculateTotals(listOf(line1, line2))

    assertThat(salesB.totalExclTax).isEqualByComparingTo("350.00")
    assertThat(salesB.totalVat).isEqualByComparingTo("70.00")
    assertThat(salesB.totalInclTax).isEqualByComparingTo("420.00")
  }
}
