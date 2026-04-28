package fr.axl.lvy.invoice

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneRepository
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneRepository
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
class InvoiceNetstoneServiceTest {

  @Autowired lateinit var invoiceNetstoneService: InvoiceNetstoneService
  @Autowired lateinit var invoiceNetstoneRepository: InvoiceNetstoneRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var testData: TestDataFactory

  @BeforeEach
  fun ensureOwnCompanies() {
    if (clientService.findDefaultCodigCompany().isEmpty) {
      val codig = Client("CLI-IN-OWN-COD", "Codig")
      codig.type = Client.ClientType.OWN_COMPANY
      codig.role = Client.ClientRole.OWN_COMPANY
      codig.visibleCompany = User.Company.CODIG
      codig.billingAddress = "Codig Bill\n75001 Paris"
      clientService.save(codig)
    }
    if (clientService.findDefaultCodigSupplier().isEmpty) {
      val supplier = Client("CLI-IN-OWN-NET", "Netstone")
      supplier.type = Client.ClientType.OWN_COMPANY
      supplier.role = Client.ClientRole.OWN_COMPANY
      supplier.visibleCompany = User.Company.NETSTONE
      clientService.save(supplier)
    }
  }

  private fun createSaleAndOrder(suffix: String): Triple<SalesNetstone, OrderNetstone, SalesCodig> {
    val client = testData.createClient("CLI-IN-$suffix", "1 Bill St", "1 Ship St")
    val orderCodig = OrderCodig("PO-IN-$suffix", client, LocalDate.of(2026, 3, 1))
    orderCodig.status = OrderCodig.OrderCodigStatus.DELIVERED
    orderCodigRepository.save(orderCodig)

    val orderNetstone = OrderNetstone("PON-IN-$suffix", orderCodig)
    orderNetstone.orderDate = LocalDate.of(2026, 3, 2)
    orderNetstone.status = OrderNetstone.OrderNetstoneStatus.RECEIVED
    val savedOrderNetstone = orderNetstoneRepository.save(orderNetstone)

    val salesCodig = SalesCodig("SO-IN-$suffix", client, LocalDate.of(2026, 3, 5))
    salesCodig.orderCodig = orderCodig
    val savedSalesCodig = salesCodigRepository.save(salesCodig)

    val salesNetstone = SalesNetstone("SON-IN-$suffix", savedSalesCodig)
    salesNetstone.orderNetstone = savedOrderNetstone
    salesNetstone.saleDate = LocalDate.of(2026, 3, 6)
    salesNetstone.expectedDeliveryDate = LocalDate.of(2026, 4, 10)
    salesNetstone.notes = "from-sale"
    val savedSalesNetstone = salesNetstoneRepository.save(salesNetstone)
    return Triple(savedSalesNetstone, savedOrderNetstone, savedSalesCodig)
  }

  @Test
  fun prepareForSale_returns_existing_invoice_when_already_linked() {
    val (sale, order, _) = createSaleAndOrder("PFS1")
    val codig = clientService.findDefaultCodigCompany().orElseThrow()
    val existing =
      InvoiceNetstone(
        "FN-EXIST-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        LocalDate.of(2026, 3, 10),
      )
    existing.orderNetstone = order
    invoiceNetstoneRepository.saveAndFlush(existing)

    val prepared = invoiceNetstoneService.prepareForSale(sale, order)

    assertThat(prepared.id).isEqualTo(existing.id)
    assertThat(prepared.internalInvoiceNumber).isEqualTo("FN-EXIST-01")
  }

  @Test
  fun prepareForSale_creates_draft_when_no_invoice_exists() {
    val (sale, order, _) = createSaleAndOrder("PFS2")

    val prepared = invoiceNetstoneService.prepareForSale(sale, order)

    assertThat(prepared.id).isNull()
    assertThat(prepared.internalInvoiceNumber).isBlank()
    assertThat(prepared.recipientType).isEqualTo(InvoiceNetstone.RecipientType.COMPANY_CODIG)
    assertThat(prepared.orderNetstone?.id).isEqualTo(order.id)
    assertThat(prepared.dueDate).isEqualTo(sale.expectedDeliveryDate)
    assertThat(prepared.notes).isEqualTo("from-sale")
    assertThat(prepared.billingAddress).isEqualTo("Codig Bill\n75001 Paris")
  }

  @Test
  fun saveWithLines_allocates_year_scoped_invoice_number_for_new_invoice() {
    val (sale, order, _) = createSaleAndOrder("NUM1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)

    val saved = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    assertThat(saved.internalInvoiceNumber).startsWith("NST_INV/2026/")
  }

  @Test
  fun saveWithLines_recalculates_totals_from_lines() {
    val (sale, order, _) = createSaleAndOrder("TOT1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)

    val line = DocumentLine(DocumentLine.DocumentType.INVOICE_NETSTONE, 0L, "Item")
    line.quantity = BigDecimal("4")
    line.unitPriceExclTax = BigDecimal("25.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    val saved = invoiceNetstoneService.saveWithLines(invoice, listOf(line))

    assertThat(saved.totalExclTax).isEqualByComparingTo("100.00")
    assertThat(saved.totalVat).isEqualByComparingTo("20.00")
    assertThat(saved.totalInclTax).isEqualByComparingTo("120.00")
  }

  @Test
  fun saveWithLines_preserves_supplier_invoice_number() {
    val (sale, order, _) = createSaleAndOrder("SIN1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    invoice.supplierInvoiceNumber = "SUPPLIER-12345"

    val saved = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    assertThat(saved.supplierInvoiceNumber).isEqualTo("SUPPLIER-12345")
    val reloaded = invoiceNetstoneRepository.findById(saved.id!!).orElseThrow()
    assertThat(reloaded.supplierInvoiceNumber).isEqualTo("SUPPLIER-12345")
  }

  @Test
  fun saveWithLines_preserves_paid_status_on_update() {
    val (sale, order, _) = createSaleAndOrder("PAID1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    invoice.status = InvoiceNetstone.InvoiceNetstoneStatus.VERIFIED
    val firstSave = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    firstSave.status = InvoiceNetstone.InvoiceNetstoneStatus.PAID
    val updated = invoiceNetstoneService.saveWithLines(firstSave, emptyList())

    assertThat(updated.status).isEqualTo(InvoiceNetstone.InvoiceNetstoneStatus.PAID)
    val reloaded = invoiceNetstoneRepository.findById(updated.id!!).orElseThrow()
    assertThat(reloaded.status).isEqualTo(InvoiceNetstone.InvoiceNetstoneStatus.PAID)
  }

  @Test
  fun saveWithLines_preserves_disputed_status_on_update() {
    val (sale, order, _) = createSaleAndOrder("DISP1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    val firstSave = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    firstSave.status = InvoiceNetstone.InvoiceNetstoneStatus.DISPUTED
    val updated = invoiceNetstoneService.saveWithLines(firstSave, emptyList())

    assertThat(updated.status).isEqualTo(InvoiceNetstone.InvoiceNetstoneStatus.DISPUTED)
  }

  @Test
  fun saveWithLines_throws_when_order_netstone_missing() {
    val codig = clientService.findDefaultCodigCompany().orElseThrow()
    val invoice =
      InvoiceNetstone(
        "",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        LocalDate.of(2026, 3, 1),
      )
    // Intentionally do not link orderNetstone.

    assertThatThrownBy { invoiceNetstoneService.saveWithLines(invoice, emptyList()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("rattachée à une commande")
  }

  @Test
  fun saveWithLines_links_order_invoice_back_reference() {
    val (sale, order, _) = createSaleAndOrder("LINK1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)

    val saved = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    val reloaded = orderNetstoneRepository.findById(order.id!!).orElseThrow()
    assertThat(reloaded.invoiceNetstone?.id).isEqualTo(saved.id)
  }

  @Test
  fun saveWithLines_replaces_existing_lines_on_update() {
    val (sale, order, _) = createSaleAndOrder("REP1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)

    val line1 = DocumentLine(DocumentLine.DocumentType.INVOICE_NETSTONE, 0L, "First")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("50.00")
    line1.discountPercent = BigDecimal.ZERO
    line1.vatRate = BigDecimal("20.00")
    val firstSave = invoiceNetstoneService.saveWithLines(invoice, listOf(line1))

    val line2 = DocumentLine(DocumentLine.DocumentType.INVOICE_NETSTONE, 0L, "Second")
    line2.quantity = BigDecimal("2")
    line2.unitPriceExclTax = BigDecimal("10.00")
    line2.discountPercent = BigDecimal.ZERO
    line2.vatRate = BigDecimal("0.00")
    val secondSave = invoiceNetstoneService.saveWithLines(firstSave, listOf(line2))

    val lines = invoiceNetstoneService.findLines(secondSave.id!!)
    assertThat(lines).hasSize(1)
    assertThat(lines[0].designation).isEqualTo("Second")
  }

  @Test
  fun saveWithLines_does_not_advance_invoice_number_on_update() {
    val (sale, order, _) = createSaleAndOrder("NUM2")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    val first = invoiceNetstoneService.saveWithLines(invoice, emptyList())
    val originalNumber = first.internalInvoiceNumber

    val second = invoiceNetstoneService.saveWithLines(first, emptyList())

    assertThat(second.internalInvoiceNumber).isEqualTo(originalNumber)
  }

  @Test
  fun findByOrderNetstoneId_returns_invoice() {
    val (sale, order, _) = createSaleAndOrder("FBO1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    invoiceNetstoneService.saveWithLines(invoice, emptyList())

    val found = invoiceNetstoneService.findByOrderNetstoneId(order.id!!)

    assertThat(found).isNotNull
    assertThat(found!!.orderNetstone?.id).isEqualTo(order.id)
  }

  @Test
  fun findByOrderNetstoneId_returns_null_when_no_invoice() {
    val (_, order, _) = createSaleAndOrder("FBO2")

    assertThat(invoiceNetstoneService.findByOrderNetstoneId(order.id!!)).isNull()
  }

  @Test
  fun findById_returns_invoice() {
    val (sale, order, _) = createSaleAndOrder("FBI1")
    val invoice = invoiceNetstoneService.prepareForSale(sale, order)
    invoice.invoiceDate = LocalDate.of(2026, 7, 12)
    val saved = invoiceNetstoneService.saveWithLines(invoice, emptyList())

    val found = invoiceNetstoneService.findById(saved.id!!)

    assertThat(found).isPresent
    assertThat(found.get().internalInvoiceNumber).isEqualTo(saved.internalInvoiceNumber)
  }

  @Test
  fun findById_returns_empty_when_unknown() {
    assertThat(invoiceNetstoneService.findById(-1L)).isEmpty
  }

  @Test
  fun previewNextInvoiceNumber_does_not_advance_real_sequence() {
    val date = LocalDate.of(2026, 8, 1)
    val preview1 = invoiceNetstoneService.previewNextInvoiceNumber(date)
    val preview2 = invoiceNetstoneService.previewNextInvoiceNumber(date)

    assertThat(preview1).isEqualTo(preview2)
    assertThat(preview1).startsWith("NST_INV/2026/")
  }

  @Test
  fun findByOrderNetstoneId_returns_latest_when_multiple_invoices_exist() {
    val (_, order, _) = createSaleAndOrder("MULTI1")
    val codig = clientService.findDefaultCodigCompany().orElseThrow()
    val first =
      InvoiceNetstone(
        "FN-MULTI-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        LocalDate.of(2026, 3, 1),
      )
    first.orderNetstone = order
    invoiceNetstoneRepository.saveAndFlush(first)
    val second =
      InvoiceNetstone(
        "FN-MULTI-02",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        LocalDate.of(2026, 4, 1),
      )
    second.orderNetstone = order
    invoiceNetstoneRepository.saveAndFlush(second)

    val found = invoiceNetstoneService.findByOrderNetstoneId(order.id!!)

    assertThat(found?.id).isEqualTo(second.id)
  }

  @Test
  fun findByOrderNetstoneId_excludes_soft_deleted() {
    val (_, order, _) = createSaleAndOrder("SD1")
    val codig = clientService.findDefaultCodigCompany().orElseThrow()
    val invoice =
      InvoiceNetstone(
        "FN-SD-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        LocalDate.of(2026, 3, 1),
      )
    invoice.orderNetstone = order
    invoiceNetstoneRepository.saveAndFlush(invoice)
    invoice.softDelete()
    invoiceNetstoneRepository.saveAndFlush(invoice)

    assertThat(invoiceNetstoneService.findByOrderNetstoneId(order.id!!)).isNull()
  }
}
