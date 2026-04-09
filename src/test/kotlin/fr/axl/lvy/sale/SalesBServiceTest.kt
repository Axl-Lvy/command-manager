package fr.axl.lvy.sale

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.order.OrderBRepository
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
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderAService: OrderAService
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var orderBRepository: OrderBRepository
  @Autowired lateinit var testData: TestDataFactory

  private fun createSalesAWithOrder(
    number: String,
    client: Client,
    status: SalesA.SalesAStatus = SalesA.SalesAStatus.VALIDATED,
  ): SalesA {
    val sale = SalesA(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    val saved = salesARepository.save(sale)

    val order = OrderA("", client, LocalDate.of(2026, 3, 1))
    val savedOrder = orderAService.save(order)
    saved.orderA = savedOrder
    return salesARepository.saveAndFlush(saved)
  }

  @Test
  fun save_and_retrieve_sale() {
    val client = testData.createClient("CLI-SB01", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-01", client)
    val salesB = SalesB("", salesA)
    salesBService.save(salesB)

    val found = salesBService.findById(salesB.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).startsWith("NST_SO_")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = testData.createClient("CLI-SB02", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-02", client)
    val salesB = SalesB("SB-DEL-01", salesA)
    salesBService.save(salesB)

    salesBService.delete(salesB.id!!)
    salesBRepository.flush()

    assertThat(salesBService.findAll()).noneMatch { it.saleNumber == "SB-DEL-01" }
  }

  @Test
  fun createOrUpdateFromSalesA_creates_salesB_with_mto_lines() {
    val client = testData.createClient("CLI-SB03", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-03", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB1")
    val regularProduct = testData.createRegularProduct("PRD-REG-SB1")

    val mtoLine =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item",
        product = mtoProduct,
      )
    val regularLine =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "Regular Item",
        product = regularProduct,
      )

    val result =
      salesBService.createOrUpdateFromSalesA(
        salesA,
        salesA.saleDate,
        salesA.expectedDeliveryDate,
        salesA.notes,
        listOf(mtoLine, regularLine),
      )

    assertThat(result.status).isEqualTo(SalesB.SalesBStatus.DRAFT)
    assertThat(result.orderB).isNull()

    val salesBLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        result.id!!,
      )
    assertThat(salesBLines).hasSize(1)
    assertThat(salesBLines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun createOrUpdateFromSalesA_updates_existing_salesB() {
    val client = testData.createClient("CLI-SB04", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-04", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB2")
    val line =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    val first =
      salesBService.createOrUpdateFromSalesA(
        salesA,
        salesA.saleDate,
        salesA.expectedDeliveryDate,
        salesA.notes,
        listOf(line),
      )
    val firstId = first.id!!

    val line2 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_A,
        salesA.id!!,
        "MTO Item Updated",
        product = mtoProduct,
        unitPrice = BigDecimal("200.00"),
      )

    val second =
      salesBService.createOrUpdateFromSalesA(
        salesA,
        salesA.saleDate,
        salesA.expectedDeliveryDate,
        salesA.notes,
        listOf(line2),
      )

    assertThat(second.id).isEqualTo(firstId)
  }

  @Test
  fun syncGeneratedOrder_creates_orderB() {
    val client = testData.createClient("CLI-SB05", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-05", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB3")
    val salesB = SalesB("SB-SYNC-01", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line =
      testData.createDocumentLine(
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
    val client = testData.createClient("CLI-SB06", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-06", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB4")
    val salesB = SalesB("SB-SYNC-02", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line1 =
      testData.createDocumentLine(
        DocumentLine.DocumentType.SALES_B,
        savedSalesB.id!!,
        "MTO Item",
        product = mtoProduct,
      )

    salesBService.syncGeneratedOrder(savedSalesB, listOf(line1))
    val firstOrderBId = savedSalesB.orderB!!.id!!

    val line2 =
      testData.createDocumentLine(
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
    val client = testData.createClient("CLI-SB07", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-07", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB5")
    val salesB = SalesB("SB-DEL-02", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)
    salesB.status = SalesB.SalesBStatus.VALIDATED
    val savedSalesB = salesBRepository.saveAndFlush(salesB)

    val line =
      testData.createDocumentLine(
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
    val client = testData.createClient("CLI-SB08", "123 Billing St", "456 Shipping Ave")
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

  @Test
  fun saveWithLines_creates_salesB_with_lines_without_syncing_order_when_not_validated() {
    val client = testData.createClient("CLI-SB-SWL", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-SWL", client)
    val salesB = SalesB("", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SWL")
    val line = DocumentLine(DocumentLine.DocumentType.SALES_B, 0L, "MTO Item")
    line.product = mtoProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesBService.saveWithLines(salesB, listOf(line))

    assertThat(saved.saleNumber).startsWith("NST_SO_")
    assertThat(saved.totalExclTax).isEqualByComparingTo("200.00")
    assertThat(saved.orderB).isNull()

    val lines = salesBService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("MTO Item")
  }

  @Test
  fun saveWithLines_replaces_existing_lines() {
    val client = testData.createClient("CLI-SB-SWL2", "123 Billing St", "456 Shipping Ave")
    val salesA = createSalesAWithOrder("SA-SB-SWL2", client)
    val salesB = SalesB("SB-SWL-01", salesA)
    salesB.saleDate = LocalDate.of(2026, 3, 1)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SWL2")
    val line1 = DocumentLine(DocumentLine.DocumentType.SALES_B, 0L, "Item A")
    line1.product = mtoProduct
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("100.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    salesBService.saveWithLines(salesB, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.SALES_B, 0L, "Item B")
    line2.product = mtoProduct
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("50.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("20.00")
    val saved = salesBService.saveWithLines(salesB, listOf(line2))

    val lines = salesBService.findLines(saved.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Item B")
  }
}
