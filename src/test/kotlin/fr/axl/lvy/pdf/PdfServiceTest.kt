package fr.axl.lvy.pdf

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.user.User
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class PdfServiceTest {

  @Autowired lateinit var pdfService: PdfService
  @Autowired lateinit var orderNetstoneService: OrderNetstoneService
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository
  @Autowired lateinit var testData: TestDataFactory

  private val sampleLogoData =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

  private val sampleLogoDataWithGenericContentType =
    sampleLogoData.replace("data:image/png;", "data:application/octet-stream;")

  @BeforeEach
  fun setupNetstoneOwnCompany() {
    val defaultSupplier = clientService.findDefaultCodigSupplier()
    if (defaultSupplier.isEmpty) {
      val netstone =
        Client("CLI-PDF-NET", "Netstone").apply {
          type = Client.ClientType.OWN_COMPANY
          role = Client.ClientRole.OWN_COMPANY
          visibleCompany = User.Company.NETSTONE
          billingAddress = "10/F., Guangdong Investment Tower\nHong Kong"
          logoData = sampleLogoData
        }
      clientService.save(netstone)
    } else {
      defaultSupplier.get().logoData = sampleLogoData
    }
  }

  /**
   * A minimal order has all optional fields null (no incoterm, no fiscal position, no notes, no
   * lines). Verifies null-handling in the template does not throw and produces a PDF with the order
   * number.
   */
  @Test
  fun generatePdf_minimal_order_produces_valid_pdf() {
    val client = clientRepository.save(Client("CLI-PDF-MIN", "Minimal Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-MIN", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    val text = extractText(pdfService.generateOrderNetstonePdf(saved.id!!))

    assertThat(text).contains(saved.orderNumber)
  }

  /** Verifies that the own-company logo saved on the company record is rendered into the PDF. */
  @Test
  fun generatePdf_uses_own_company_logo() {
    val client = clientRepository.save(Client("CLI-PDF-LOGO", "Logo Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-LOGO", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    val imageCount = countImages(pdfService.generateOrderNetstonePdf(saved.id!!))

    assertThat(imageCount).isGreaterThan(0)
  }

  /** Verifies uploaded logos still render when the browser provides a generic content type. */
  @Test
  fun generatePdf_normalizes_logo_content_type() {
    clientService.findDefaultCodigSupplier().orElseThrow().logoData =
      sampleLogoDataWithGenericContentType
    val client = clientRepository.save(Client("CLI-PDF-LOGO-TYPE", "Logo Type Client"))
    val orderCodig =
      orderCodigRepository.save(OrderCodig("CA-PDF-LOGO-TYPE", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    val imageCount = countImages(pdfService.generateOrderNetstonePdf(saved.id!!))

    assertThat(imageCount).isGreaterThan(0)
  }

  /**
   * A fully populated order exercises all optional fields: incoterm, fiscal position, notes, and a
   * line item. Verifies that every section of the template renders its data correctly.
   */
  @Test
  fun generatePdf_fully_populated_order_renders_all_sections() {
    val client =
      clientRepository.save(
        Client("CLI-PDF-FULL", "Zenji Pharmaceuticals").apply {
          billingAddress = "122 Xuqing Road\nSuzhou, 215000\nChine"
        }
      )
    val orderCodig =
      OrderCodig("CA-PDF-FULL", client, LocalDate.of(2026, 1, 10)).apply { currency = "USD" }
    orderCodigRepository.save(orderCodig)

    val fiscalPosition = fiscalPositionRepository.save(FiscalPosition("Import/Export Hors Europe"))
    val supplier =
      clientRepository.save(
        Client("CLI-PDF-SUP", "Supplier Chemicals").apply {
          role = Client.ClientRole.PRODUCER
          billingAddress = "88 Supplier Road\nBangkok\nThailand"
          notes = "Supplier note line"
        }
      )

    val order =
      OrderNetstone("", orderCodig).apply {
        this.supplier = supplier
        incoterms = "CIF"
        incotermLocation = "ANTWERP"
        deliveryLocation = "Port of Antwerp"
        expectedDeliveryDate = LocalDate.of(2026, 6, 11)
        this.fiscalPosition = fiscalPosition
        notes = "One batch per pallet"
      }

    val line =
      DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "N-METHYLGLUCAMINE").apply {
        quantity = BigDecimal("8000.00")
        unit = "kg"
        unitPriceExclTax = BigDecimal("10.50")
        discountPercent = BigDecimal.ZERO
        vatRate = BigDecimal.ZERO
        recalculate()
      }

    val saved = orderNetstoneService.saveWithLines(order, listOf(line))
    val text = extractText(pdfService.generateOrderNetstonePdf(saved.id!!))

    // header
    assertThat(text).contains(saved.orderNumber)
    assertThat(text).contains("Netstone")
    // supplier block
    assertThat(text).contains("Supplier Chemicals")
    assertThat(text).contains("88 Supplier Road")
    assertThat(text).doesNotContain("Zenji Pharmaceuticals")
    assertThat(text).contains("DELIVERY ADDRESS:")
    assertThat(text).doesNotContain("Dellvery")
    assertThat(text).doesNotContain("Port of:")
    // metadata
    assertThat(text).contains("CIF")
    assertThat(text).contains("ANTWERP")
    // line item
    assertThat(text).contains("N-METHYLGLUCAMINE")
    assertThat(text).contains("10,50")
    // totals  (8000 × 10.50 = 84 000)
    assertThat(text).contains("84")
    // footer
    assertThat(text).contains("Import/Export Hors Europe")
    assertThat(text).contains("One batch per pallet")
    assertThat(text).contains("Supplier note line AND INSURANCE")
  }

  private fun extractText(bytes: ByteArray): String =
    PDDocument.load(ByteArrayInputStream(bytes)).use { doc -> PDFTextStripper().getText(doc) }

  private fun countImages(bytes: ByteArray): Int =
    PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
      doc.pages.sumOf { countImages(it.resources) }
    }

  private fun countImages(resources: PDResources?): Int {
    if (resources == null) return 0
    return resources.xObjectNames.sumOf { name ->
      when (val xObject = resources.getXObject(name)) {
        is PDImageXObject -> 1
        is PDFormXObject -> countImages(xObject.resources)
        else -> 0
      }
    }
  }
}
