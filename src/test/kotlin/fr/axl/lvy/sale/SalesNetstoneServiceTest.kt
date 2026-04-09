package fr.axl.lvy.sale

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstoneRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class SalesNetstoneServiceTest {

  @Autowired lateinit var salesNetstoneService: SalesNetstoneService
  @Autowired lateinit var salesCodigService: SalesCodigService
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderCodigService: OrderCodigService
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createSalesCodigWithOrder(
    number: String,
    client: Client,
    status: SalesStatus = SalesStatus.VALIDATED,
  ): SalesCodig {
    val sale = SalesCodig(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    val saved = salesCodigRepository.save(sale)

    val order = OrderCodig("", client, LocalDate.of(2026, 3, 1))
    val savedOrder = orderCodigService.save(order)
    saved.orderCodig = savedOrder
    return salesCodigRepository.saveAndFlush(saved)
  }

  @Test
  fun save_and_retrieve_sale() {
    val client = testData.createClient("CLI-SB01", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-01", client)
    val salesNetstone = SalesNetstone("", salesCodig)
    salesNetstoneService.save(salesNetstone)

    val found = salesNetstoneService.findById(salesNetstone.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).startsWith("NST_SO_")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-SB02", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-02", client)
    val salesNetstone = SalesNetstone("SB-DEL-01", salesCodig)
    salesNetstoneService.save(salesNetstone)

    salesNetstoneService.delete(salesNetstone.id!!)
    salesNetstoneRepository.flush()

    assertThat(salesNetstoneService.findAll()).noneMatch { it.saleNumber == "SB-DEL-01" }
  }

  @Test
  fun createOrUpdateFromSalesCodig_creates_salesNetstone_with_mto_lines() {
    val client = testData.createClient("CLI-SB03", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-03", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB1")
    val regularProduct = testData.createRegularProduct("PRD-REG-SB1")

    val mtoLine =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        salesCodig.id!!,
        "MTO Item",
        product = mtoProduct,
      )
    val regularLine =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        salesCodig.id!!,
        "Regular Item",
        product = regularProduct,
      )

    val result =
      salesNetstoneService.createOrUpdateFromSalesCodig(
        salesCodig,
        salesCodig.saleDate,
        salesCodig.expectedDeliveryDate,
        salesCodig.notes,
        listOf(mtoLine, regularLine),
      )

    assertThat(result.status).isEqualTo(SalesStatus.DRAFT)
    assertThat(result.orderNetstone).isNull()

    val salesNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_NETSTONE,
        result.id!!,
      )
    assertThat(salesNetstoneLines).hasSize(1)
    assertThat(salesNetstoneLines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun createOrUpdateFromSalesCodig_updates_existing_salesNetstone() {
    val client = testData.createClient("CLI-SB04", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-04", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB2")
    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        salesCodig.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    val first =
      salesNetstoneService.createOrUpdateFromSalesCodig(
        salesCodig,
        salesCodig.saleDate,
        salesCodig.expectedDeliveryDate,
        salesCodig.notes,
        listOf(line),
      )
    val firstId = first.id!!

    val line2 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_CODIG,
        salesCodig.id!!,
        "MTO Item Updated",
        product = mtoProduct,
        unitPrice = BigDecimal("200.00"),
      )

    val second =
      salesNetstoneService.createOrUpdateFromSalesCodig(
        salesCodig,
        salesCodig.saleDate,
        salesCodig.expectedDeliveryDate,
        salesCodig.notes,
        listOf(line2),
      )

    assertThat(second.id).isEqualTo(firstId)
  }

  @Test
  fun syncGeneratedOrder_creates_orderNetstone() {
    val client = testData.createClient("CLI-SB05", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-05", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB3")
    val salesNetstone = SalesNetstone("SB-SYNC-01", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)
    salesNetstone.status = SalesStatus.VALIDATED
    val savedSalesNetstone = salesNetstoneRepository.saveAndFlush(salesNetstone)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSalesNetstone.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    val result = salesNetstoneService.syncGeneratedOrder(savedSalesNetstone, listOf(line))

    assertThat(result.orderNetstone).isNotNull
    val orderNetstone = result.orderNetstone!!
    assertThat(orderNetstone.orderNumber).startsWith("NST_PO_")

    val orderNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        orderNetstone.id!!,
      )
    assertThat(orderNetstoneLines).hasSize(1)
    assertThat(orderNetstoneLines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun syncGeneratedOrder_updates_existing_orderNetstone() {
    val client = testData.createClient("CLI-SB06", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-06", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB4")
    val salesNetstone = SalesNetstone("SB-SYNC-02", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)
    salesNetstone.status = SalesStatus.VALIDATED
    val savedSalesNetstone = salesNetstoneRepository.saveAndFlush(salesNetstone)

    val line1 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSalesNetstone.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    salesNetstoneService.syncGeneratedOrder(savedSalesNetstone, listOf(line1))
    val firstOrderNetstoneId = savedSalesNetstone.orderNetstone!!.id!!

    val line2 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSalesNetstone.id!!,
        "MTO Item v2",
        product = mtoProduct,
      )

    salesNetstoneService.syncGeneratedOrder(savedSalesNetstone, listOf(line2))

    assertThat(savedSalesNetstone.orderNetstone!!.id).isEqualTo(firstOrderNetstoneId)
  }

  @Test
  fun deleteBySalesCodigId_soft_deletes_salesNetstone_and_orderNetstone() {
    val client = testData.createClient("CLI-SB07", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-07", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB5")
    val salesNetstone = SalesNetstone("SB-DEL-02", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)
    salesNetstone.status = SalesStatus.VALIDATED
    val savedSalesNetstone = salesNetstoneRepository.saveAndFlush(salesNetstone)

    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_NETSTONE,
        savedSalesNetstone.id!!,
        "MTO Item",
        product = mtoProduct,
      )
    salesNetstoneService.syncGeneratedOrder(savedSalesNetstone, listOf(line))
    salesNetstoneRepository.flush()

    val orderNetstoneId = savedSalesNetstone.orderNetstone!!.id!!

    salesNetstoneService.deleteBySalesCodigId(salesCodig.id!!)
    salesNetstoneRepository.flush()

    val deletedSalesNetstone =
      salesNetstoneRepository.findById(savedSalesNetstone.id!!).orElseThrow()
    assertThat(deletedSalesNetstone.isDeleted()).isTrue

    val deletedOrderNetstone = orderNetstoneRepository.findById(orderNetstoneId).orElseThrow()
    assertThat(deletedOrderNetstone.isDeleted()).isTrue
  }

  @Test
  fun recalculateTotals_sums_lines() {
    val client = testData.createClient("CLI-SB08", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-08", client)
    val salesNetstone = SalesNetstone("SB-CALC-01", salesCodig)
    salesNetstoneService.save(salesNetstone)

    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, salesNetstone.id!!, "Item 1")
    line1.quantity = BigDecimal("2")
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    line1.recalculate()

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, salesNetstone.id!!, "Item 2")
    line2.quantity = BigDecimal("3")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    line2.recalculate()

    salesNetstone.recalculateTotals(listOf(line1, line2))

    assertThat(salesNetstone.totalExclTax).isEqualByComparingTo("350.00")
    assertThat(salesNetstone.totalVat).isEqualByComparingTo("70.00")
    assertThat(salesNetstone.totalInclTax).isEqualByComparingTo("420.00")
  }

  @Test
  fun saveWithLines_creates_salesNetstone_with_lines_without_syncing_order_when_not_validated() {
    val client = testData.createClient("CLI-SB-SWL", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SWL", client)
    val salesNetstone = SalesNetstone("", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SWL")
    val line = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "MTO Item")
    line.product = mtoProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesNetstoneService.saveWithLines(salesNetstone, listOf(line))

    assertThat(saved.saleNumber).startsWith("NST_SO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("200.00")
    assertThat(saved.orderNetstone).isNull()

    val lines = salesNetstoneService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val client = testData.createClient("CLI-SB-SWL2", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SWL2", client)
    val salesNetstone = SalesNetstone("SB-SWL-01", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SWL2")
    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "Item A")
    line1.product = mtoProduct
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    salesNetstoneService.saveWithLines(salesNetstone, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "Item B")
    line2.product = mtoProduct
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    val saved = salesNetstoneService.saveWithLines(salesNetstone, listOf(line2))

    val lines = salesNetstoneService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Item B")
  }
}
