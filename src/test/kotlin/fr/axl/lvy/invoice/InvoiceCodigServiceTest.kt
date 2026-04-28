package fr.axl.lvy.invoice

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.user.User
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class InvoiceCodigServiceTest {

  @Autowired lateinit var invoiceCodigService: InvoiceCodigService
  @Autowired lateinit var invoiceCodigRepository: InvoiceCodigRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var testData: TestDataFactory

  @BeforeEach
  fun ensureOwnCompanies() {
    if (clientService.findDefaultCodigCompany().isEmpty) {
      val codig = Client("CLI-IC-OWN-COD", "Codig")
      codig.type = Client.ClientType.OWN_COMPANY
      codig.role = Client.ClientRole.OWN_COMPANY
      codig.visibleCompany = User.Company.CODIG
      clientService.save(codig)
    }
  }

  private fun createSaleAndOrder(
    suffix: String,
    orderStatus: OrderCodig.OrderCodigStatus = OrderCodig.OrderCodigStatus.DELIVERED,
  ): Pair<SalesCodig, OrderCodig> {
    val client = testData.createClient("CLI-IC-$suffix", "1 Bill St", "1 Ship St")
    val order = OrderCodig("PO-IC-$suffix", client, LocalDate.of(2026, 3, 1))
    order.status = orderStatus
    order.billingAddress = "Order Bill\n75001 Paris"
    val savedOrder = orderCodigRepository.save(order)

    val sale = SalesCodig("SO-IC-$suffix", client, LocalDate.of(2026, 3, 5))
    sale.orderCodig = savedOrder
    sale.expectedDeliveryDate = LocalDate.of(2026, 4, 5)
    sale.currency = "EUR"
    sale.incoterms = "FOB"
    val savedSale = salesCodigRepository.save(sale)
    return savedSale to savedOrder
  }

  @Test
  fun prepareForSale_returns_existing_invoice_when_already_linked() {
    val (sale, order) = createSaleAndOrder("PFS1")
    val existing = InvoiceCodig("FA-EXIST-01", sale.client, LocalDate.of(2026, 3, 10))
    existing.orderCodig = order
    invoiceCodigRepository.saveAndFlush(existing)

    val prepared = invoiceCodigService.prepareForSale(sale, order)

    assertThat(prepared.id).isEqualTo(existing.id)
    assertThat(prepared.invoiceNumber).isEqualTo("FA-EXIST-01")
  }

  @Test
  fun prepareForSale_returns_draft_when_no_invoice_exists() {
    val (sale, order) = createSaleAndOrder("PFS2")

    val prepared = invoiceCodigService.prepareForSale(sale, order)

    assertThat(prepared.id).isNull()
    assertThat(prepared.invoiceNumber).isBlank()
    assertThat(prepared.client.id).isEqualTo(sale.client.id)
    assertThat(prepared.orderCodig?.id).isEqualTo(order.id)
    assertThat(prepared.dueDate).isEqualTo(sale.expectedDeliveryDate)
    assertThat(prepared.currency).isEqualTo(sale.currency)
    assertThat(prepared.incoterms).isEqualTo(sale.incoterms)
  }

  @Test
  fun saveWithLines_allocates_year_scoped_invoice_number_for_new_invoice() {
    val (sale, order) = createSaleAndOrder("NUM1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)

    val saved = invoiceCodigService.saveWithLines(invoice, emptyList())

    assertThat(saved.invoiceNumber).startsWith("CoD_INV/2026/")
  }

  @Test
  fun saveWithLines_recalculates_totals_from_lines() {
    val (sale, order) = createSaleAndOrder("TOT1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)

    val line = DocumentLine(DocumentLine.DocumentType.INVOICE_CODIG, 0L, "Item")
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = invoiceCodigService.saveWithLines(invoice, listOf(line))

    assertThat(saved.totalExclTax).isEqualByComparingTo("200.00")
    assertThat(saved.totalVat).isEqualByComparingTo("40.00")
    assertThat(saved.totalInclTax).isEqualByComparingTo("240.00")
  }

  @Test
  fun saveWithLines_replaces_existing_lines_on_update() {
    val (sale, order) = createSaleAndOrder("REP1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)

    val line1 = DocumentLine(DocumentLine.DocumentType.INVOICE_CODIG, 0L, "First")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("50.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    val firstSave = invoiceCodigService.saveWithLines(invoice, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.INVOICE_CODIG, 0L, "Second")
    line2.quantity = BigDecimal("3")
    line2.unitPriceExclTax = BigDecimal("10.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("5.50")
    val secondSave = invoiceCodigService.saveWithLines(firstSave, listOf(line2))

    val lines = invoiceCodigService.findLines(secondSave.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Second")
    assertThat(secondSave.totalExclTax).isEqualByComparingTo("30.00")
  }

  @Test
  fun saveWithLines_does_not_advance_invoice_number_on_update() {
    val (sale, order) = createSaleAndOrder("REP2")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    val first = invoiceCodigService.saveWithLines(invoice, emptyList())
    val originalNumber = first.invoiceNumber

    val second = invoiceCodigService.saveWithLines(first, emptyList())

    assertThat(second.invoiceNumber).isEqualTo(originalNumber)
  }

  @Test
  fun saveWithLines_keeps_order_status_when_invoice_is_draft() {
    val (sale, order) = createSaleAndOrder("DR1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    invoice.status = InvoiceCodig.InvoiceCodigStatus.DRAFT

    invoiceCodigService.saveWithLines(invoice, emptyList())

    val reloaded = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(reloaded.status).isEqualTo(OrderCodig.OrderCodigStatus.DELIVERED)
  }

  @Test
  fun saveWithLines_advances_order_status_to_invoiced_when_invoice_validated() {
    val (sale, order) = createSaleAndOrder("VAL1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    invoice.status = InvoiceCodig.InvoiceCodigStatus.ISSUED

    invoiceCodigService.saveWithLines(invoice, emptyList())

    val reloaded = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(reloaded.status).isEqualTo(OrderCodig.OrderCodigStatus.INVOICED)
  }

  @Test
  fun saveWithLines_preserves_paid_status_on_update() {
    val (sale, order) = createSaleAndOrder("PAID1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    invoice.status = InvoiceCodig.InvoiceCodigStatus.ISSUED
    val firstSave = invoiceCodigService.saveWithLines(invoice, emptyList())

    firstSave.status = InvoiceCodig.InvoiceCodigStatus.PAID
    val updated = invoiceCodigService.saveWithLines(firstSave, emptyList())

    assertThat(updated.status).isEqualTo(InvoiceCodig.InvoiceCodigStatus.PAID)
    val reloaded = invoiceCodigRepository.findById(updated.id!!).orElseThrow()
    assertThat(reloaded.status).isEqualTo(InvoiceCodig.InvoiceCodigStatus.PAID)
  }

  @Test
  fun saveWithLines_throws_when_order_codig_missing() {
    val client = testData.createClient("CLI-IC-NOORD")
    val invoice = InvoiceCodig("", client, LocalDate.of(2026, 3, 1))
    // Intentionally do not link orderCodig.

    assertThatThrownBy { invoiceCodigService.saveWithLines(invoice, emptyList()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("rattachée à une commande")
  }

  @Test
  fun saveWithLines_links_order_invoice_back_reference() {
    val (sale, order) = createSaleAndOrder("LINK1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    invoice.status = InvoiceCodig.InvoiceCodigStatus.ISSUED

    val saved = invoiceCodigService.saveWithLines(invoice, emptyList())

    val reloaded = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(reloaded.invoice?.id).isEqualTo(saved.id)
  }

  @Test
  fun findByOrderCodigId_returns_invoice() {
    val (sale, order) = createSaleAndOrder("FBO1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    invoiceCodigService.saveWithLines(invoice, emptyList())

    val found = invoiceCodigService.findByOrderCodigId(order.id!!)

    assertThat(found).isNotNull
    assertThat(found!!.orderCodig?.id).isEqualTo(order.id)
  }

  @Test
  fun findByOrderCodigId_returns_null_when_no_invoice() {
    val (_, order) = createSaleAndOrder("FBO2")

    assertThat(invoiceCodigService.findByOrderCodigId(order.id!!)).isNull()
  }

  @Test
  fun findById_returns_invoice() {
    val (sale, order) = createSaleAndOrder("FBI1")
    val invoice = invoiceCodigService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 5, 12)
    val saved = invoiceCodigService.saveWithLines(invoice, emptyList())

    val found = invoiceCodigService.findById(saved.id!!)

    assertThat(found).isPresent
    assertThat(found.get().invoiceNumber).isEqualTo(saved.invoiceNumber)
  }

  @Test
  fun findById_returns_empty_when_unknown() {
    assertThat(invoiceCodigService.findById(-1L)).isEmpty
  }

  @Test
  fun previewNextInvoiceNumber_does_not_advance_real_sequence() {
    val date = LocalDate.of(2026, 6, 1)
    val preview1 = invoiceCodigService.previewNextInvoiceNumber(date)
    val preview2 = invoiceCodigService.previewNextInvoiceNumber(date)

    assertThat(preview1).isEqualTo(preview2)
    assertThat(preview1).startsWith("CoD_INV/2026/")
  }

  @Test
  fun findByOrderCodigId_returns_latest_when_multiple_invoices_exist() {
    val (_, order) = createSaleAndOrder("MULTI1")
    val first = InvoiceCodig("FA-MULTI-01", order.client, LocalDate.of(2026, 3, 1))
    first.orderCodig = order
    invoiceCodigRepository.saveAndFlush(first)
    val second = InvoiceCodig("FA-MULTI-02", order.client, LocalDate.of(2026, 4, 1))
    second.orderCodig = order
    invoiceCodigRepository.saveAndFlush(second)

    val found = invoiceCodigService.findByOrderCodigId(order.id!!)

    assertThat(found?.id).isEqualTo(second.id)
  }

  @Test
  fun findByOrderCodigId_excludes_soft_deleted() {
    val (_, order) = createSaleAndOrder("SD1")
    val invoice = InvoiceCodig("FA-SD-01", order.client, LocalDate.of(2026, 3, 1))
    invoice.orderCodig = order
    invoiceCodigRepository.saveAndFlush(invoice)
    invoice.softDelete()
    invoiceCodigRepository.saveAndFlush(invoice)

    assertThat(invoiceCodigService.findByOrderCodigId(order.id!!)).isNull()
  }
}
