package fr.axl.lvy.pdf

import fr.axl.lvy.TestDataFactory
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.delivery.DeliveryNoteCodig
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.DeliveryNoteNetstone
import fr.axl.lvy.delivery.DeliveryNoteNetstoneService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService
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
  @Autowired lateinit var orderCodigService: OrderCodigService
  @Autowired lateinit var orderNetstoneService: OrderNetstoneService
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository
  @Autowired lateinit var deliveryNoteCodigService: DeliveryNoteCodigService
  @Autowired lateinit var deliveryNoteNetstoneService: DeliveryNoteNetstoneService
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesCodigService: SalesCodigService
  @Autowired lateinit var salesNetstoneService: SalesNetstoneService
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var productService: ProductService
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

  /** Verifies the delivery note PDF renders the Netstone delivery-specific logistics fields. */
  @Test
  fun generateDeliveryPdf_renders_delivery_note_sections() {
    val codigCompany =
      clientService.findDefaultCodigCompany().orElseGet {
        clientService.save(
          Client("CLI-PDF-CODIG", "CoDIG SAS").apply {
            type = Client.ClientType.OWN_COMPANY
            role = Client.ClientRole.OWN_COMPANY
            visibleCompany = User.Company.CODIG
          }
        )
      }
    codigCompany.billingAddress = "12 rue de Paris\n75001 Paris\nFrance"
    codigCompany.vatNumber = "FR12345678901"
    clientService.save(codigCompany)

    val customer = clientRepository.save(Client("CLI-PDF-DEL-CUST", "Final Customer"))
    val orderCodig =
      orderCodigRepository.save(
        OrderCodig("Cod_PO_001", customer, LocalDate.of(2026, 2, 10)).apply { currency = "USD" }
      )
    val salesCodig =
      salesCodigRepository.save(
        SalesCodig("SO-CODIG-PDF", customer, LocalDate.of(2026, 2, 10)).apply {
          this.orderCodig = orderCodig
          shippingAddress = "Warehouse A\nLe Havre\nFrance"
        }
      )
    val sale =
      SalesNetstone("NST_SO_001", salesCodig).apply {
        shippingAddress = "Warehouse A\nLe Havre\nFrance"
        incoterms = "CFR"
        incotermLocation = "Le Havre"
      }
    val product =
      productRepository.save(
        Product("PRD-PDF-DEL", "N-METHYLGLUCAMINE").apply {
          unit = "kg"
          hsCode = "292219"
          madeIn = "China"
          specifications = "White crystalline powder"
        }
      )
    val saleLine =
      DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, product.name).apply {
        this.product = product
        quantity = BigDecimal("100.00")
        unit = "kg"
        hsCode = product.hsCode
        madeIn = product.madeIn
        clientProductCode = "PC-OLD-001"
        recalculate()
      }
    val savedSale = salesNetstoneService.saveWithLines(sale, listOf(saleLine))
    product.replaceClientProductCodes(listOf(codigCompany to "PC-CURRENT-001"))
    productService.save(product)
    val order =
      orderNetstoneService.save(
        OrderNetstone("NST-PO-PDF", orderCodig).apply {
          incoterms = "CFR"
          incotermLocation = "Le Havre"
          deliveryLocation = savedSale.shippingAddress
        }
      )
    val delivery =
      DeliveryNoteNetstone("", order).apply {
        arrivalDate = LocalDate.of(2026, 5, 4)
        billOfLading = "BL-7788"
        containerNumber = "CONT-42"
        seals = "SEAL-99"
        lot = "LOT-A"
      }
    val deliveryLine =
      DocumentLine(DocumentLine.DocumentType.DELIVERY_NETSTONE, 0L, product.name).apply {
        this.product = product
        quantity = BigDecimal("80.00")
        unit = "kg"
        hsCode = product.hsCode
        madeIn = product.madeIn
        clientProductCode = "PC-OLD-001"
        recalculate()
      }
    val savedDelivery = deliveryNoteNetstoneService.saveWithLines(delivery, listOf(deliveryLine))

    val text = extractText(pdfService.generateDeliveryNetstonePdf(savedDelivery.id!!))

    assertThat(text).contains(savedDelivery.deliveryNoteNumber)
    assertThat(text).contains("DELIVERY ADDRESS:")
    assertThat(text).contains("Warehouse A")
    assertThat(text).contains("CUSTOMER ADDRESS:")
    assertThat(text).contains("CoDIG SAS")
    assertThat(text).contains("TVA: FR12345678901")
    assertThat(text).contains("NST_SO_001")
    assertThat(text).contains("04/05/2026")
    assertThat(text).contains("Cod_PO_001")
    assertThat(text).contains("CFR Le Havre")
    assertThat(text).contains("N-METHYLGLUCAMINE")
    assertThat(text).contains("100,00 kg")
    assertThat(text).contains("80,00 kg")
    assertThat(text).contains("PO : Cod_PO_001 / CFR Le Havre / Made in : China")
    assertThat(text).contains("White crystalline powder")
    assertThat(text).contains("PC: PC-CURRENT-001")
    assertThat(text).doesNotContain("PC: PC-OLD-001")
    assertThat(text).contains("HS CODE : 292219")
    assertThat(text).contains("BL : BL-7788 / CONT-42")
    assertThat(text).doesNotContain("N° de conteneur")
    assertThat(text).contains("SEALS : SEAL-99")
    assertThat(text).contains("LOT : LOT-A")
  }

  /** Verifies the Codig delivery note PDF uses the customer delivery address and client PO ref. */
  @Test
  fun generateCodigDeliveryPdf_renders_customer_delivery_and_client_reference() {
    val codigCompany =
      clientService.findDefaultCodigCompany().orElseGet {
        clientService.save(
          Client("CLI-PDF-CODIG-DLV", "CoDIG SAS").apply {
            type = Client.ClientType.OWN_COMPANY
            role = Client.ClientRole.OWN_COMPANY
            visibleCompany = User.Company.CODIG
          }
        )
      }
    codigCompany.billingAddress = "12 rue de Paris\n75001 Paris\nFrance"
    codigCompany.vatNumber = "FRCODIG123"
    codigCompany.logoData = sampleLogoData
    clientService.save(codigCompany)

    val customer =
      clientRepository.save(
        Client("CLI-PDF-CODIG-CUST", "Customer Delivery").apply {
          billingAddress = "1 Buyer Road\nLondon"
        }
      )
    val orderCodig =
      orderCodigRepository.save(
        OrderCodig("COD-ORDER-PDF", customer, LocalDate.of(2026, 3, 1)).apply {
          clientReference = "PO-CUSTOMER-777"
          shippingAddress = "Customer Warehouse\nRotterdam\nNetherlands"
          incoterms = "DAP"
          incotermLocation = "Rotterdam"
        }
      )
    val sale =
      salesCodigRepository.save(
        SalesCodig("cod_SO_001", customer, LocalDate.of(2026, 3, 1)).apply {
          this.orderCodig = orderCodig
          clientReference = "PO-CUSTOMER-777"
          shippingAddress = orderCodig.shippingAddress
          incoterms = orderCodig.incoterms
          incotermLocation = orderCodig.incotermLocation
        }
      )
    val product =
      productRepository.save(
        Product("PRD-PDF-CODIG-DLV", "SODIUM GLUCONATE").apply {
          unit = "kg"
          hsCode = "291816"
          madeIn = "China"
          specifications = "Technical grade"
        }
      )
    product.replaceClientProductCodes(listOf(customer to "PC-CODIG-001"))
    productService.save(product)

    val saleLine =
      DocumentLine(DocumentLine.DocumentType.SALES_CODIG, 0L, product.name).apply {
        this.product = product
        quantity = BigDecimal("50.00")
        unit = "kg"
        hsCode = product.hsCode
        madeIn = product.madeIn
        recalculate()
      }
    documentLineRepository.save(
      DocumentLine(DocumentLine.DocumentType.SALES_CODIG, sale.id!!, saleLine.designation).apply {
        copyFieldsFrom(saleLine)
      }
    )
    salesCodigRepository.flush()
    val orderLine =
      DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, 0L, product.name).apply {
        this.product = product
        quantity = BigDecimal("50.00")
        unit = "kg"
        hsCode = product.hsCode
        madeIn = product.madeIn
        recalculate()
      }
    documentLineRepository.save(
      DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, orderCodig.id!!, orderLine.designation)
        .apply { copyFieldsFrom(orderLine) }
    )
    val delivery =
      deliveryNoteCodigService.save(
        DeliveryNoteCodig("", orderCodig, customer).apply {
          shippingAddress = orderCodig.shippingAddress
          arrivalDate = LocalDate.of(2026, 5, 8)
          billOfLading = "BL-CODIG-001"
          containerNumber = "CONT-CODIG"
          seals = "SEAL-CODIG"
          lot = "LOT-CODIG"
        }
      )

    val text = extractText(pdfService.generateDeliveryCodigPdf(delivery.id!!))

    assertThat(text).contains(delivery.deliveryNoteNumber)
    assertThat(text).contains("TVA: FRCODIG123")
    assertThat(text).contains("DELIVERY ADDRESS:")
    assertThat(text).contains("Customer Warehouse")
    assertThat(text).doesNotContain("CUSTOMER ADDRESS:")
    assertThat(text).contains("cod_SO_001")
    assertThat(text).contains("08/05/2026")
    assertThat(text).contains("PO-CUSTOMER-777")
    assertThat(text).contains("DAP Rotterdam")
    assertThat(text).contains("SODIUM GLUCONATE")
    assertThat(text).contains("50,00 kg")
    assertThat(text).contains("PO : PO-CUSTOMER-777 / DAP Rotterdam / Made in : China")
    assertThat(text).contains("Technical grade")
    assertThat(text).contains("PC: PC-CODIG-001")
    assertThat(text).contains("HS CODE : 291816")
    assertThat(text).contains("BL : BL-CODIG-001 / CONT-CODIG")
    assertThat(text).contains("SEALS : SEAL-CODIG")
    assertThat(text).contains("LOT : LOT-CODIG")
  }

  /**
   * No uploaded logo and no classpath logo → generated wordmark fallback still renders an image.
   */
  @Test
  fun generatePdf_falls_back_to_generated_logo_when_no_uploaded_logo() {
    clientService.findDefaultCodigSupplier().orElseThrow().logoData = null
    val client = clientRepository.save(Client("CLI-PDF-NOLOGO", "No Logo Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-NOLOGO", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    val imageCount = countImages(pdfService.generateOrderNetstonePdf(saved.id!!))

    assertThat(imageCount).isGreaterThan(0)
  }

  /** Malformed base64 payload → normalization fails → fallback renders. */
  @Test
  fun generatePdf_falls_back_when_logo_is_invalid_base64() {
    clientService.findDefaultCodigSupplier().orElseThrow().logoData =
      "data:image/png;base64,!!!not-valid-base64!!!"
    val client = clientRepository.save(Client("CLI-PDF-BADB64", "Bad Base64 Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-BADB64", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    assertThat(countImages(pdfService.generateOrderNetstonePdf(saved.id!!))).isGreaterThan(0)
  }

  /** Missing `;base64,` marker → normalization rejects → fallback renders. */
  @Test
  fun generatePdf_falls_back_when_logo_has_no_base64_marker() {
    clientService.findDefaultCodigSupplier().orElseThrow().logoData = "data:image/png,abcdef"
    val client = clientRepository.save(Client("CLI-PDF-NOMARK", "No Marker Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-NOMARK", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    assertThat(countImages(pdfService.generateOrderNetstonePdf(saved.id!!))).isGreaterThan(0)
  }

  /** Missing `data:` prefix → normalization rejects → fallback renders. */
  @Test
  fun generatePdf_falls_back_when_logo_has_no_data_prefix() {
    clientService.findDefaultCodigSupplier().orElseThrow().logoData =
      "image/png;base64,iVBORw0KGgo="
    val client = clientRepository.save(Client("CLI-PDF-NODP", "No Data Prefix Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-NODP", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    assertThat(countImages(pdfService.generateOrderNetstonePdf(saved.id!!))).isGreaterThan(0)
  }

  /**
   * Unknown magic bytes AND unknown declared content type → normalization rejects → fallback
   * renders.
   */
  @Test
  fun generatePdf_falls_back_when_logo_format_is_unknown() {
    val gibberish =
      java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    clientService.findDefaultCodigSupplier().orElseThrow().logoData =
      "data:image/webp;base64,$gibberish"
    val client = clientRepository.save(Client("CLI-PDF-UNKN", "Unknown Format Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-UNKN", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    assertThat(countImages(pdfService.generateOrderNetstonePdf(saved.id!!))).isGreaterThan(0)
  }

  /**
   * `image/jpg` is a common but non-standard content type. Verify it normalizes to `image/jpeg`.
   */
  @Test
  fun generatePdf_accepts_declared_image_jpg_content_type() {
    val jpegOut = java.io.ByteArrayOutputStream()
    val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
    javax.imageio.ImageIO.write(img, "jpg", jpegOut)
    val encoded = java.util.Base64.getEncoder().encodeToString(jpegOut.toByteArray())
    clientService.findDefaultCodigSupplier().orElseThrow().logoData =
      "data:image/jpg;base64,$encoded"
    val client = clientRepository.save(Client("CLI-PDF-JPG", "Jpg Client"))
    val orderCodig = orderCodigRepository.save(OrderCodig("CA-PDF-JPG", client, LocalDate.now()))
    val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), emptyList())

    assertThat(countImages(pdfService.generateOrderNetstonePdf(saved.id!!))).isGreaterThan(0)
  }

  /** Uncommon currency codes pass through as-is; known codes render their symbol. */
  @Test
  fun generatePdf_renders_various_currency_symbols() {
    listOf("EUR" to "€", "USD" to "$", "GBP" to "£", "CNY" to "¥", "RMB" to "¥", "JPY" to "JPY")
      .forEachIndexed { index, (code, expected) ->
        val client = clientRepository.save(Client("CLI-PDF-CUR-$index", "Currency Client $code"))
        val orderCodig =
          orderCodigRepository.save(
            OrderCodig("CA-PDF-CUR-$index", client, LocalDate.now()).apply { currency = code }
          )
        val line =
          DocumentLine(DocumentLine.DocumentType.ORDER_NETSTONE, 0L, "Item").apply {
            quantity = BigDecimal.ONE
            unitPriceExclTax = BigDecimal("42.00")
            vatRate = BigDecimal.ZERO
            discountPercent = BigDecimal.ZERO
            recalculate()
          }
        val saved = orderNetstoneService.saveWithLines(OrderNetstone("", orderCodig), listOf(line))

        val text = extractText(pdfService.generateOrderNetstonePdf(saved.id!!))

        assertThat(text).contains(expected)
      }
  }

  /** Null incoterm + null location should render without error and without stray separator. */
  @Test
  fun generateDeliveryCodigPdf_handles_missing_incoterm_and_sale() {
    val customer = clientRepository.save(Client("CLI-PDF-NOSALE", "No Sale Customer"))
    val orderCodig =
      orderCodigRepository.save(
        OrderCodig("COD-NO-SALE", customer, LocalDate.of(2026, 4, 1)).apply {
          clientReference = "PO-DIRECT-42"
          shippingAddress = "Dock 7\nHamburg"
        }
      )
    val delivery =
      deliveryNoteCodigService.save(
        DeliveryNoteCodig("", orderCodig, customer).apply {
          shippingAddress = orderCodig.shippingAddress
          arrivalDate = LocalDate.of(2026, 4, 15)
        }
      )

    val text = extractText(pdfService.generateDeliveryCodigPdf(delivery.id!!))

    assertThat(text).contains(delivery.deliveryNoteNumber)
    assertThat(text).contains("Dock 7")
    assertThat(text).contains("PO-DIRECT-42")
  }

  /** Unknown note id → IllegalArgumentException. */
  @Test
  fun generateDeliveryNetstonePdf_throws_for_unknown_note() {
    org.assertj.core.api.Assertions.assertThatThrownBy {
        pdfService.generateDeliveryNetstonePdf(-1L)
      }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  /** Unknown note id → IllegalArgumentException. */
  @Test
  fun generateDeliveryCodigPdf_throws_for_unknown_note() {
    org.assertj.core.api.Assertions.assertThatThrownBy { pdfService.generateDeliveryCodigPdf(-1L) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  /** Unknown order id → IllegalArgumentException. */
  @Test
  fun generateOrderNetstonePdf_throws_for_unknown_order() {
    org.assertj.core.api.Assertions.assertThatThrownBy { pdfService.generateOrderNetstonePdf(-1L) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  /**
   * Delivery line with no product + sale line sharing only the designation. Exercises the
   * designation-match fallback in DeliveryPdfLine.from.
   */
  @Test
  fun generateDeliveryNetstonePdf_matches_sale_line_by_designation_when_no_product_on_delivery() {
    val customer = clientRepository.save(Client("CLI-PDF-DSN", "Designation Match"))
    val orderCodig =
      orderCodigRepository.save(OrderCodig("COD-DSN", customer, LocalDate.of(2026, 6, 1)))
    val salesCodig =
      salesCodigRepository.save(
        SalesCodig("SO-DSN", customer, LocalDate.of(2026, 6, 1)).apply {
          this.orderCodig = orderCodig
        }
      )
    val saleProduct = productRepository.save(Product("PRD-DSN", "Designation Widget"))
    val saleLine =
      DocumentLine(DocumentLine.DocumentType.SALES_NETSTONE, 0L, saleProduct.name).apply {
        this.product = saleProduct
        quantity = BigDecimal("42.00")
        unit = "kg"
        recalculate()
      }
    val sale =
      salesNetstoneService.saveWithLines(SalesNetstone("NST-DSN", salesCodig), listOf(saleLine))
    val order = orderNetstoneService.save(OrderNetstone("NST-PO-DSN", orderCodig))
    val deliveryLine =
      DocumentLine(DocumentLine.DocumentType.DELIVERY_NETSTONE, 0L, saleProduct.name).apply {
        quantity = BigDecimal("30.00")
        unit = "kg"
        recalculate()
      }
    val savedDelivery =
      deliveryNoteNetstoneService.saveWithLines(
        DeliveryNoteNetstone("", order),
        listOf(deliveryLine),
      )

    val text = extractText(pdfService.generateDeliveryNetstonePdf(savedDelivery.id!!))

    assertThat(text).contains("Designation Widget")
    assertThat(text).contains("42,00 kg")
    assertThat(text).contains("30,00 kg")
    assertThat(sale.id).isNotNull
  }

  /**
   * Product has a client code for a client that is neither the CoDIG company nor the customer.
   * Verifies the fallback to findFirstClientProductCode when no pc-client matches.
   */
  @Test
  fun generateDeliveryNetstonePdf_falls_back_to_first_product_code_when_no_pc_client_matches() {
    val unrelated = clientRepository.save(Client("CLI-UNREL", "Unrelated Ref Holder"))
    val customer = clientRepository.save(Client("CLI-PDF-FB", "Fallback Customer"))
    val orderCodig =
      orderCodigRepository.save(OrderCodig("COD-FB", customer, LocalDate.of(2026, 7, 1)))
    val product =
      productRepository.save(
        Product("PRD-FB", "Fallback Product").apply {
          replaceClientProductCodes(listOf(unrelated to "PC-FALLBACK"))
        }
      )
    productService.save(product)
    val order = orderNetstoneService.save(OrderNetstone("NST-PO-FB", orderCodig))
    val deliveryLine =
      DocumentLine(DocumentLine.DocumentType.DELIVERY_NETSTONE, 0L, product.name).apply {
        this.product = product
        quantity = BigDecimal("5.00")
        unit = "kg"
        recalculate()
      }
    val savedDelivery =
      deliveryNoteNetstoneService.saveWithLines(
        DeliveryNoteNetstone("", order),
        listOf(deliveryLine),
      )

    val text = extractText(pdfService.generateDeliveryNetstonePdf(savedDelivery.id!!))

    assertThat(text).contains("PC: PC-FALLBACK")
  }

  /**
   * Codig delivery whose order has no linked sale. Exercises the `sale == null` branch and uses
   * `order.clientReference` as the customer reference.
   */
  @Test
  fun generateDeliveryCodigPdf_without_linked_sale_uses_order_client_reference() {
    val customer = clientRepository.save(Client("CLI-CD-NOSALE", "Direct Customer"))
    val orderCodig =
      orderCodigRepository.save(
        OrderCodig("COD-NO-SALE-2", customer, LocalDate.of(2026, 8, 1)).apply {
          clientReference = "PO-DIRECT-123"
          shippingAddress = "Dock 9\nRotterdam"
        }
      )
    val product =
      productRepository.save(
        Product("PRD-CD-NS", "Direct Product").apply {
          unit = "kg"
          madeIn = "France"
        }
      )
    val orderLine =
      DocumentLine(DocumentLine.DocumentType.ORDER_CODIG, orderCodig.id!!, product.name).apply {
        this.product = product
        quantity = BigDecimal("10.00")
        unit = "kg"
        recalculate()
      }
    documentLineRepository.save(orderLine)
    val delivery =
      deliveryNoteCodigService.save(
        DeliveryNoteCodig("", orderCodig, customer).apply {
          shippingAddress = orderCodig.shippingAddress
        }
      )

    val text = extractText(pdfService.generateDeliveryCodigPdf(delivery.id!!))

    assertThat(text).contains("PO-DIRECT-123")
    assertThat(text).contains("Direct Product")
    assertThat(text).contains("10,00 kg")
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
