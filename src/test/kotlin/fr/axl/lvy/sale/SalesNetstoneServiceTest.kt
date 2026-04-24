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
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneRepository
import fr.axl.lvy.order.OrderNetstoneService
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class SalesNetstoneServiceTest {

  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var salesNetstoneService: SalesNetstoneService
  @Autowired lateinit var salesCodigService: SalesCodigService
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var orderCodigService: OrderCodigService
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneService: OrderNetstoneService
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var testData: TestDataFactory

  @BeforeEach
  fun ensureNetstoneOwnCompany() {
    if (clientService.findDefaultCodigSupplier().isEmpty) {
      val netstone = Client("CLI-SB-OWN-NET", "Netstone")
      netstone.type = Client.ClientType.OWN_COMPANY
      netstone.role = Client.ClientRole.OWN_COMPANY
      netstone.visibleCompany = fr.axl.lvy.user.User.Company.NETSTONE
      clientService.save(netstone)
    }
  }

  private fun createSalesCodigWithOrder(
    number: String,
    client: Client,
    status: SalesStatus = SalesStatus.VALIDATED,
  ): SalesCodig {
    val sale = SalesCodig(number, client, LocalDate.of(2026, 3, 1))
    sale.status = status
    val saved = salesCodigRepository.save(sale)

    val order = OrderCodig("", client, LocalDate.of(2026, 3, 1))
    order.incoterms = "FOB"
    order.incotermLocation = "Le Havre"
    order.currency = "USD"
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
  fun findAll_paginated_excludes_soft_deleted() {
    val client = testData.createClient("CLI-SB-PAGE", "a", "b")
    val salesCodig = createSalesCodigWithOrder("SA-SB-PAGE", client)
    val kept = SalesNetstone("SB-PAGE-KEEP", salesCodig)
    val gone = SalesNetstone("SB-PAGE-GONE", salesCodig)
    salesNetstoneService.save(kept)
    salesNetstoneService.save(gone)
    salesNetstoneService.delete(gone.id!!)
    salesNetstoneRepository.flush()

    val page = salesNetstoneService.findAll(PageRequest.of(0, 100))
    assertThat(page.content).anyMatch { it.id == kept.id }
    assertThat(page.content).noneMatch { it.id == gone.id }
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
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Fiscalite Netstone"))
    val netstone = clientService.findDefaultCodigSupplier().orElseThrow()
    netstone.fiscalPosition = fiscalPosition
    clientService.save(netstone)

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
    assertThat(result.incotermLocation).isEqualTo("Le Havre")
    assertThat(result.fiscalPosition?.position).isEqualTo("Fiscalite Netstone")
    assertThat(result.currency).isEqualTo("USD")

    val salesNetstoneLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_NETSTONE,
        result.id!!,
      )
    assertThat(salesNetstoneLines).hasSize(1)
    assertThat(salesNetstoneLines[0].designation).isEqualTo("MTO Item")
    assertThat(salesNetstoneLines[0].discountPercent).isEqualByComparingTo("0.00")
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
    val supplier = Client("CLI-SB-SUP", "Supplier Generated")
    supplier.role = Client.ClientRole.PRODUCER
    clientService.save(supplier)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB3")
    mtoProduct.replaceSuppliers(listOf(supplier))
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
    assertThat(orderNetstone.supplier?.id).isEqualTo(supplier.id)

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
  fun saveWithLines_validated_without_mto_does_not_create_orderNetstone() {
    val client = testData.createClient("CLI-SB-NO-MTO", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-NO-MTO", client)
    val salesNetstone = SalesNetstone("", salesCodig)
    salesNetstone.saleDate = LocalDate.of(2026, 3, 1)
    salesNetstone.status = SalesStatus.VALIDATED

    val regularProduct = testData.createRegularProduct("PRD-REG-NO-MTO")
    val line = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "Regular Item")
    line.product = regularProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("50.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesNetstoneService.saveWithLines(salesNetstone, listOf(line))

    assertThat(saved.orderNetstone).isNull()
  }

  @Test
  fun findBySalesCodigId_returns_linked_salesNetstone() {
    val client = testData.createClient("CLI-SB-FIND", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-FIND", client)
    val salesNetstone = SalesNetstone("SB-FIND-01", salesCodig)
    salesNetstoneService.save(salesNetstone)

    val found = salesNetstoneService.findBySalesCodigId(salesCodig.id!!)
    assertThat(found).isPresent
    assertThat(found.get().saleNumber).isEqualTo("SB-FIND-01")
  }

  @Test
  fun findBySalesCodigId_excludes_soft_deleted() {
    val client = testData.createClient("CLI-SB-SD", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SD", client)
    val salesNetstone = SalesNetstone("SB-SD-01", salesCodig)
    salesNetstoneService.save(salesNetstone)

    salesNetstoneService.delete(salesNetstone.id!!)
    salesNetstoneRepository.flush()

    val found = salesNetstoneService.findBySalesCodigId(salesCodig.id!!)
    assertThat(found).isEmpty
  }

  @Test
  fun save_throws_when_salesCodig_has_no_linked_orderCodig() {
    val client = testData.createClient("CLI-SB-INV", "123 Billing", "456 Shipping")
    val salesCodig = SalesCodig("SA-INV-01", client, LocalDate.of(2026, 3, 1))
    salesCodigRepository.save(salesCodig) // no orderCodig set

    val sale = SalesNetstone("", salesCodig)

    assertThatThrownBy { salesNetstoneService.save(sale) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Order Codig")
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

  @Test
  fun createOrUpdateFromSalesCodig_does_not_overwrite_fields_when_netstone_sale_past_draft() {
    val client = testData.createClient("CLI-SB-GATE-01", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-GATE-01", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB-GATE-01")
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
        "initial-notes",
        listOf(line),
      )

    first.status = SalesStatus.VALIDATED
    first.notes = "user-edited"
    salesNetstoneRepository.saveAndFlush(first)

    val second =
      salesNetstoneService.createOrUpdateFromSalesCodig(
        salesCodig,
        salesCodig.saleDate,
        salesCodig.expectedDeliveryDate,
        "upstream-modified",
        listOf(line),
      )

    assertThat(second.id).isEqualTo(first.id)
    assertThat(second.status).isEqualTo(SalesStatus.VALIDATED)
    assertThat(second.notes).isEqualTo("user-edited")
  }

  @Test
  fun findDetailedById_returns_sale() {
    val client = testData.createClient("CLI-SB-DET")
    val salesCodig = createSalesCodigWithOrder("SA-SB-DET", client)
    val sale = SalesNetstone("SB-DET-01", salesCodig)
    salesNetstoneService.save(sale)

    val found = salesNetstoneService.findDetailedById(sale.id!!)

    assertThat(found).isPresent
    assertThat(found.get().saleNumber).isEqualTo("SB-DET-01")
  }

  @Test
  fun findDetailedById_returns_empty_when_missing() {
    assertThat(salesNetstoneService.findDetailedById(-1L)).isEmpty
  }

  @Test
  fun findByOrderCodigId_returns_linked_salesNetstone() {
    val client = testData.createClient("CLI-SB-FBO")
    val salesCodig = createSalesCodigWithOrder("SA-SB-FBO", client)
    val sale = SalesNetstone("SB-FBO-01", salesCodig)
    salesNetstoneService.save(sale)

    val found = salesNetstoneService.findByOrderCodigId(salesCodig.orderCodig!!.id!!)

    assertThat(found).isPresent
    assertThat(found.get().id).isEqualTo(sale.id)
  }

  @Test
  fun save_preserves_preset_fields_and_uses_orderCodig_fallbacks() {
    val client = testData.createClient("CLI-SB-SAVE-F")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SAVE-F", client)
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("preset FP"))
    val sale = SalesNetstone("", salesCodig)
    sale.incotermLocation = "preset-location"
    sale.fiscalPosition = fiscalPosition
    sale.currency = "GBP"

    val saved = salesNetstoneService.save(sale)

    assertThat(saved.saleNumber).startsWith("NST_SO_")
    assertThat(saved.incotermLocation).isEqualTo("preset-location")
    assertThat(saved.fiscalPosition?.id).isEqualTo(fiscalPosition.id)
    assertThat(saved.currency).isEqualTo("GBP")
  }

  @Test
  fun save_falls_back_to_orderCodig_when_incotermLocation_and_currency_blank() {
    val client = testData.createClient("CLI-SB-SAVE-FB")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SAVE-FB", client)
    val sale = SalesNetstone("SB-SAVE-FB-01", salesCodig)
    sale.incotermLocation = ""
    sale.currency = ""

    val saved = salesNetstoneService.save(sale)

    assertThat(saved.incotermLocation).isEqualTo("Le Havre")
    assertThat(saved.currency).isEqualTo("USD")
  }

  @Test
  fun deleteBySalesCodigId_is_noop_when_no_salesNetstone_exists() {
    val client = testData.createClient("CLI-SB-NOOP")
    val salesCodig = createSalesCodigWithOrder("SA-SB-NOOP", client)

    salesNetstoneService.deleteBySalesCodigId(salesCodig.id!!)

    assertThat(salesNetstoneRepository.findBySalesCodigId(salesCodig.id!!)).isNull()
  }

  @Test
  fun deleteBySalesCodigId_without_orderNetstone_soft_deletes_sale_only() {
    val client = testData.createClient("CLI-SB-NO-ORDER")
    val salesCodig = createSalesCodigWithOrder("SA-SB-NO-ORDER", client)
    val sale = SalesNetstone("SB-NO-ORDER-01", salesCodig)
    salesNetstoneService.save(sale)

    salesNetstoneService.deleteBySalesCodigId(salesCodig.id!!)
    salesNetstoneRepository.flush()

    val reloaded = salesNetstoneRepository.findById(sale.id!!).orElseThrow()
    assertThat(reloaded.isDeleted()).isTrue
  }

  @Test
  fun saveWithLines_validated_with_mto_syncs_orderNetstone() {
    val client = testData.createClient("CLI-SB-SWL-MTO")
    val salesCodig = createSalesCodigWithOrder("SA-SB-SWL-MTO", client)
    val supplier = Client("CLI-SB-SWL-SUP", "Supplier SWL")
    supplier.role = Client.ClientRole.PRODUCER
    clientService.save(supplier)
    val mtoProduct = testData.createMtoProduct("PRD-SB-SWL-MTO")
    mtoProduct.replaceSuppliers(listOf(supplier))

    val sale = SalesNetstone("", salesCodig)
    sale.saleDate = LocalDate.of(2026, 3, 1)
    sale.status = SalesStatus.VALIDATED

    val line = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "MTO Widget")
    line.product = mtoProduct
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = salesNetstoneService.saveWithLines(sale, listOf(line))

    assertThat(saved.orderNetstone).isNotNull
    assertThat(saved.orderNetstone!!.orderNumber).startsWith("NST_PO_")
  }

  @Test
  fun saveWithLines_cleans_up_existing_sent_order_when_no_longer_validated_with_mto() {
    val client = testData.createClient("CLI-SB-CLEAN")
    val salesCodig = createSalesCodigWithOrder("SA-SB-CLEAN", client)
    val supplier = Client("CLI-SB-CLEAN-SUP", "Supplier Clean")
    supplier.role = Client.ClientRole.PRODUCER
    clientService.save(supplier)
    val mtoProduct = testData.createMtoProduct("PRD-SB-CLEAN")
    mtoProduct.replaceSuppliers(listOf(supplier))

    val sale = SalesNetstone("SB-CLEAN-01", salesCodig)
    sale.saleDate = LocalDate.of(2026, 3, 1)
    sale.status = SalesStatus.VALIDATED
    val mtoLine = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "MTO Widget")
    mtoLine.product = mtoProduct
    mtoLine.quantity = BigDecimal("2")
    mtoLine.unitPriceExclTax = BigDecimal("100.00")
    mtoLine.discountPercent = BigDecimal.ZERO
    mtoLine.vatRate = BigDecimal("20.00")
    val withOrder = salesNetstoneService.saveWithLines(sale, listOf(mtoLine))
    val orderId = withOrder.orderNetstone!!.id!!
    salesNetstoneRepository.flush()

    withOrder.status = SalesStatus.DRAFT
    val regularProduct = testData.createRegularProduct("PRD-SB-CLEAN-REG")
    val regularLine = DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, "Regular Item")
    regularLine.product = regularProduct
    regularLine.quantity = BigDecimal("1")
    regularLine.unitPriceExclTax = BigDecimal("50.00")
    regularLine.discountPercent = BigDecimal.ZERO
    regularLine.vatRate = BigDecimal("20.00")

    val cleaned = salesNetstoneService.saveWithLines(withOrder, listOf(regularLine))
    salesNetstoneRepository.flush()

    assertThat(cleaned.orderNetstone).isNull()
    val deletedOrder = orderNetstoneRepository.findById(orderId).orElseThrow()
    assertThat(deletedOrder.isDeleted()).isTrue
  }

  @Test
  fun syncGeneratedOrder_does_not_overwrite_fields_when_netstone_order_past_sent() {
    val client = testData.createClient("CLI-SB-GATE-02", "123 Billing St", "456 Shipping Ave")
    val salesCodig = createSalesCodigWithOrder("SA-SB-GATE-02", client)

    val mtoProduct = testData.createMtoProduct("PRD-MTO-SB-GATE-02")
    val salesNetstone = SalesNetstone("SB-GATE-SYNC-01", salesCodig)
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
    val orderNetstone = savedSalesNetstone.orderNetstone!!
    orderNetstoneService.changeStatus(orderNetstone, OrderNetstone.OrderNetstoneStatus.CONFIRMED)
    orderNetstone.notes = "supplier-confirmed-edits"
    orderNetstoneRepository.saveAndFlush(orderNetstone)

    savedSalesNetstone.notes = "user-changed-on-sale"
    salesNetstoneService.syncGeneratedOrder(savedSalesNetstone, listOf(line))

    val reloaded = orderNetstoneRepository.findById(orderNetstone.id!!).orElseThrow()
    assertThat(reloaded.notes).isEqualTo("supplier-confirmed-edits")
    assertThat(reloaded.status).isEqualTo(OrderNetstone.OrderNetstoneStatus.CONFIRMED)
  }
}
