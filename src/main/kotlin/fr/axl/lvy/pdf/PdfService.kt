package fr.axl.lvy.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.DeliveryNoteNetstoneService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.invoice.InvoiceCodigService
import fr.axl.lvy.invoice.InvoiceNetstoneService
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstoneService
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/** Generates PDF documents from Thymeleaf templates for business objects. */
@Service
class PdfService(
  private val orderCodigService: OrderCodigService,
  private val orderNetstoneService: OrderNetstoneService,
  private val deliveryNoteCodigService: DeliveryNoteCodigService,
  private val deliveryNoteNetstoneService: DeliveryNoteNetstoneService,
  private val salesCodigService: SalesCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val invoiceCodigService: InvoiceCodigService,
  private val invoiceNetstoneService: InvoiceNetstoneService,
  private val productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
  private val clientService: ClientService,
) {
  private val templateEngine =
    TemplateEngine().apply {
      addTemplateResolver(
        ClassLoaderTemplateResolver().apply {
          prefix = "templates/pdf/"
          suffix = ".html"
          templateMode = TemplateMode.HTML
          characterEncoding = "UTF-8"
          isCacheable = false
        }
      )
    }

  /**
   * Generates a PDF for a Netstone purchase order. Loads all required associations in its own
   * read-only transaction so lazy relationships are safely accessible.
   */
  @Transactional(readOnly = true)
  fun generateOrderNetstonePdf(orderId: Long): ByteArray {
    val order =
      orderNetstoneService.findDetailedById(orderId).orElseThrow {
        IllegalArgumentException("OrderNetstone $orderId not found")
      }
    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        orderId,
      )
    val pdfLines = lines.map { PdfLine.from(it, productService) }
    val ownCompany = clientService.findDefaultCodigSupplier().orElse(null)
    val supplier = order.supplier
    val currency = order.orderCodig.currency

    val ctx = Context()
    ctx.setVariable("order", order)
    ctx.setVariable("lines", pdfLines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable("supplier", supplier)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "supplierAddressLines",
      supplier?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("deliveryLocationLines", order.deliveryLocation?.lines() ?: emptyList<String>())
    ctx.setVariable("noteLines", order.notes?.lines() ?: emptyList<String>())
    ctx.setVariable("supplierNoteLines", supplier?.notes?.lines() ?: emptyList<String>())
    ctx.setVariable("currencySymbol", currencySymbol(currency))
    ctx.setVariable("logoSrc", logoSrc(ownCompany))

    val html = templateEngine.process("order-netstone", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /**
   * Generates a PDF for a Codig customer order. Uses the Netstone order PDF structure adapted to
   * CoDIG parties and Codig order lines.
   */
  @Transactional(readOnly = true)
  fun generateOrderCodigPdf(orderId: Long): ByteArray {
    val order =
      orderCodigService.findDetailedById(orderId).orElseThrow {
        IllegalArgumentException("OrderCodig $orderId not found")
      }
    val lines = orderCodigService.findLines(orderId)
    val pdfLines = lines.map { PdfLine.from(it, productService) }
    val ownCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val client = order.client
    val linkedNetstoneSale = salesNetstoneService.findByOrderCodigId(orderId).orElse(null)
    val supplierNoteLines =
      lines
        .mapNotNull { line ->
          line.product?.id?.let { productId ->
            productService.findDetailedById(productId).orElse(null)?.let { product ->
              product.suppliers.firstOrNull { supplier -> supplier.id == order.client.id }?.notes
                ?: product.suppliers.firstOrNull()?.notes
            }
          }
        }
        .flatMap { it.lines() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val ctx = Context()
    ctx.setVariable("order", order)
    ctx.setVariable("lines", pdfLines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable("client", client)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("clientAddressLines", client.billingAddress?.lines() ?: emptyList<String>())
    ctx.setVariable(
      "deliveryLocationLines",
      order.deliveryLocation?.lines() ?: order.shippingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("noteLines", order.notes?.lines() ?: emptyList<String>())
    ctx.setVariable("conditionLines", order.conditions?.lines() ?: emptyList<String>())
    ctx.setVariable("supplierNoteLines", supplierNoteLines)
    ctx.setVariable("yourOrderRef", linkedNetstoneSale?.saleNumber ?: "")
    ctx.setVariable("currencySymbol", currencySymbol(order.currency))
    ctx.setVariable("logoSrc", logoSrc(ownCompany))

    val html = templateEngine.process("order-codig", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /**
   * Generates a PDF for a Netstone delivery note. The delivery lines provide delivered quantities;
   * the linked Netstone sale provides the original ordered quantities and delivery address.
   */
  @Transactional(readOnly = true)
  fun generateDeliveryNetstonePdf(noteId: Long): ByteArray {
    val note =
      deliveryNoteNetstoneService.findById(noteId).orElseThrow {
        IllegalArgumentException("DeliveryNoteNetstone $noteId not found")
      }
    val order =
      orderNetstoneService.findDetailedById(note.orderNetstone.id!!).orElse(note.orderNetstone)
    val sale = order.orderCodig.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) }
    val deliveryLines = deliveryNoteNetstoneService.findLines(noteId)
    val saleLines = sale?.id?.let { salesNetstoneService.findLines(it) } ?: emptyList()
    val ownCompany = clientService.findDefaultCodigSupplier().orElse(null)
    val codigCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val customer = order.orderCodig.client
    val pcClients = listOfNotNull(codigCompany, customer).distinctBy { it.id }
    val deliveryPdfLines =
      deliveryLines.map { line -> DeliveryPdfLine.from(line, saleLines, pcClients, productService) }

    val ctx = Context()
    ctx.setVariable("note", note)
    ctx.setVariable("order", order)
    ctx.setVariable("sale", sale)
    ctx.setVariable("lines", deliveryPdfLines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable("codigCompany", codigCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "deliveryAddressLines",
      sale?.shippingAddress?.lines() ?: order.deliveryLocation?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "codigAddressLines",
      codigCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable("incotermText", incotermText(order.incoterms, order.incotermLocation))
    ctx.setVariable("lotInline", lotInline(note.lot))
    ctx.setVariable("lotMultiline", lotMultiline(note.lot))

    val html = templateEngine.process("delivery-netstone", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /**
   * Generates a PDF for a Codig delivery note. The Codig sale provides the customer reference and
   * ordered quantities; the delivery note carries delivered quantities and logistics references.
   */
  @Transactional(readOnly = true)
  fun generateDeliveryCodigPdf(noteId: Long): ByteArray {
    val note =
      deliveryNoteCodigService.findById(noteId).orElseThrow {
        IllegalArgumentException("DeliveryNoteCodig $noteId not found")
      }
    val order = orderCodigService.findDetailedById(note.orderCodig.id!!).orElse(note.orderCodig)
    val sale = order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) }
    val deliveryLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_CODIG,
        order.id!!,
      )
    val saleLines = sale?.id?.let { salesCodigService.findLines(it) } ?: emptyList()
    val ownCompany = clientService.findDefaultCodigCompany().orElse(null)
    val pcClients = listOf(order.client)
    val deliveryPdfLines =
      deliveryLines.map { line -> DeliveryPdfLine.from(line, saleLines, pcClients, productService) }

    val ctx = Context()
    ctx.setVariable("note", note)
    ctx.setVariable("order", order)
    ctx.setVariable("sale", sale)
    ctx.setVariable("lines", deliveryPdfLines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("deliveryAddressLines", note.shippingAddress?.lines() ?: emptyList<String>())
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable("incotermText", incotermText(order.incoterms, order.incotermLocation))
    ctx.setVariable("customerReference", sale?.clientReference ?: order.clientReference ?: "")
    ctx.setVariable("lotInline", lotInline(note.lot))
    ctx.setVariable("lotMultiline", lotMultiline(note.lot))

    val html = templateEngine.process("delivery-codig", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /**
   * Generates a PDF for a Codig sale. Renders customer invoicing/shipping addresses, sale metadata,
   * and product lines with pricing.
   */
  @Transactional(readOnly = true)
  fun generateSalesCodigPdf(saleId: Long): ByteArray {
    val sale =
      salesCodigService.findDetailedById(saleId).orElseThrow {
        IllegalArgumentException("SalesCodig $saleId not found")
      }
    val lines = salesCodigService.findLines(saleId)
    val pdfLines = lines.map { PdfLine.from(it, productService) }
    val ownCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val client = sale.client

    val ctx = Context()
    ctx.setVariable("sale", sale)
    ctx.setVariable("client", client)
    ctx.setVariable("lines", pdfLines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "clientAddressLines",
      client.billingAddress?.lines()
        ?: sale.billingAddress?.lines()
        ?: sale.shippingAddress?.lines()
        ?: emptyList<String>(),
    )
    ctx.setVariable(
      "invoicingAddressLines",
      sale.billingAddress?.lines() ?: client.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("shippingAddressLines", sale.shippingAddress?.lines() ?: emptyList<String>())
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable("currencySymbol", currencySymbol(sale.currency))
    ctx.setVariable("incotermText", incotermText(sale.incoterms, sale.incotermLocation))

    val html = templateEngine.process("sales-codig", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /**
   * Generates a PDF for a Netstone sale. Mirrors the Codig sale PDF layout with Netstone as the
   * issuing company and CoDIG as the internal customer reference.
   */
  @Transactional(readOnly = true)
  fun generateSalesNetstonePdf(saleId: Long): ByteArray {
    val sale =
      salesNetstoneService.findDetailedById(saleId).orElseThrow {
        IllegalArgumentException("SalesNetstone $saleId not found")
      }
    val lines = salesNetstoneService.findLines(saleId).map { PdfLine.from(it, productService) }
    val ownCompany =
      clientService
        .findDefaultCodigSupplier()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val codigCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)

    val ctx = Context()
    ctx.setVariable("sale", sale)
    ctx.setVariable("client", codigCompany)
    ctx.setVariable("lines", lines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "clientAddressLines",
      codigCompany?.billingAddress?.lines()
        ?: sale.salesCodig.billingAddress?.lines()
        ?: sale.shippingAddress?.lines()
        ?: emptyList<String>(),
    )
    ctx.setVariable(
      "invoicingAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("shippingAddressLines", sale.shippingAddress?.lines() ?: emptyList<String>())
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable("currencySymbol", currencySymbol(sale.currency))
    ctx.setVariable("incotermText", incotermText(sale.incoterms, sale.incotermLocation))
    ctx.setVariable(
      "customerReference",
      sale.salesCodig.orderCodig?.orderNumber
        ?: sale.salesCodig.orderCodig?.clientReference
        ?: sale.salesCodig.clientReference
        ?: "",
    )

    val html = templateEngine.process("sales-netstone", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /** Generates a PDF for a Codig invoice. */
  @Transactional(readOnly = true)
  fun generateInvoiceCodigPdf(invoiceId: Long): ByteArray {
    val invoice =
      invoiceCodigService.findById(invoiceId).orElseThrow {
        IllegalArgumentException("InvoiceCodig $invoiceId not found")
      }
    val lines = invoiceCodigService.findLines(invoiceId).map { PdfLine.from(it, productService) }
    val sale = invoice.orderCodig?.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) }
    val netstoneDeliveryNote =
      invoice.orderCodig
        ?.id
        ?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) }
        ?.orderNetstone
        ?.id
        ?.let(deliveryNoteNetstoneService::findByOrderNetstoneId)
    val ownCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val client = sale?.client ?: invoice.client

    val ctx = Context()
    ctx.setVariable("invoice", invoice)
    ctx.setVariable("sale", sale)
    ctx.setVariable("client", client)
    ctx.setVariable("lines", lines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "clientAddressLines",
      client.billingAddress?.lines()
        ?: sale?.billingAddress?.lines()
        ?: invoice.clientAddress?.lines()
        ?: emptyList<String>(),
    )
    ctx.setVariable(
      "invoicingAddressLines",
      invoice.clientAddress?.lines() ?: client.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable("currencySymbol", currencySymbol(invoice.currency))
    ctx.setVariable("incotermText", sale?.incoterms ?: invoice.incoterms ?: "")
    ctx.setVariable("incotermLocation", sale?.incotermLocation ?: "")
    ctx.setVariable("paymentTermLabel", sale?.paymentTerm?.label ?: "")
    ctx.setVariable("customerReference", sale?.clientReference ?: "")
    ctx.setVariable("fiscalPositionRemark", sale?.fiscalPosition?.position ?: "")
    ctx.setVariable("netstoneDeliveryNote", netstoneDeliveryNote)
    ctx.setVariable("lotInline", lotInline(netstoneDeliveryNote?.lot))
    ctx.setVariable("lotMultiline", lotMultiline(netstoneDeliveryNote?.lot))
    ctx.setVariable(
      "noteLines",
      invoice.notes?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList<String>(),
    )

    val html = templateEngine.process("invoice-codig", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  /** Generates a PDF for a Netstone invoice billed to Codig. */
  @Transactional(readOnly = true)
  fun generateInvoiceNetstonePdf(invoiceId: Long): ByteArray {
    val invoice =
      invoiceNetstoneService.findById(invoiceId).orElseThrow {
        IllegalArgumentException("InvoiceNetstone $invoiceId not found")
      }
    val lines = invoiceNetstoneService.findLines(invoiceId).map { PdfLine.from(it, productService) }
    val ownCompany =
      clientService
        .findDefaultCodigSupplier()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)
    val codigCompany =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null)

    val ctx = Context()
    ctx.setVariable("invoice", invoice)
    ctx.setVariable("client", codigCompany)
    ctx.setVariable("lines", lines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "clientAddressLines",
      codigCompany?.billingAddress?.lines()
        ?: invoice.billingAddress?.lines()
        ?: emptyList<String>(),
    )
    ctx.setVariable(
      "invoicingAddressLines",
      invoice.billingAddress?.lines()
        ?: codigCompany?.billingAddress?.lines()
        ?: emptyList<String>(),
    )
    ctx.setVariable("logoSrc", logoSrc(ownCompany))
    ctx.setVariable(
      "currencySymbol",
      currencySymbol(invoice.orderNetstone?.orderCodig?.currency ?: "EUR"),
    )
    ctx.setVariable(
      "incotermText",
      incotermText(invoice.orderNetstone?.incoterms, invoice.orderNetstone?.incotermLocation),
    )
    ctx.setVariable("customerReference", invoice.orderNetstone?.orderCodig?.orderNumber ?: "")

    val html = templateEngine.process("invoice-netstone", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

  private data class DeliveryPdfLine(
    val designation: String,
    val displayName: String,
    val shortDescription: String?,
    val orderedQuantity: BigDecimal,
    val deliveredQuantity: BigDecimal,
    val unit: String?,
    val madeIn: String?,
    val specifications: String?,
    val clientProductCode: String?,
    val hsCode: String?,
  ) {
    companion object {
      fun from(
        deliveryLine: DocumentLine,
        saleLines: List<DocumentLine>,
        pcClients: List<Client>,
        productService: ProductService,
      ): DeliveryPdfLine {
        val saleLine =
          saleLines.firstOrNull { saleLine ->
            val deliveryProductId = deliveryLine.product?.id
            deliveryProductId != null && saleLine.product?.id == deliveryProductId
          } ?: saleLines.firstOrNull { it.designation == deliveryLine.designation }
        val product =
          (deliveryLine.product ?: saleLine?.product)?.id?.let {
            productService.findDetailedById(it).orElse(null)
          } ?: deliveryLine.product ?: saleLine?.product
        val productId = product?.id ?: deliveryLine.product?.id ?: saleLine?.product?.id
        return DeliveryPdfLine(
          designation = deliveryLine.designation,
          displayName = product?.label?.takeIf { it.isNotBlank() } ?: deliveryLine.designation,
          shortDescription = product?.shortDescription,
          orderedQuantity = saleLine?.quantity ?: deliveryLine.quantity,
          deliveredQuantity = deliveryLine.quantity,
          unit = deliveryLine.unit ?: saleLine?.unit,
          madeIn = deliveryLine.madeIn ?: saleLine?.madeIn ?: product?.madeIn,
          specifications =
            product?.specifications ?: deliveryLine.description ?: saleLine?.description,
          clientProductCode =
            productId?.let { currentClientProductCode(it, pcClients, productService) },
          hsCode = deliveryLine.hsCode ?: saleLine?.hsCode ?: product?.hsCode,
        )
      }

      private fun currentClientProductCode(
        productId: Long,
        pcClients: List<Client>,
        productService: ProductService,
      ): String? =
        pcClients.firstNotNullOfOrNull { client ->
          client.id?.let { productService.findClientProductCode(productId, it) }
        } ?: productService.findFirstClientProductCode(productId)
    }
  }

  private data class PdfLine(
    val designation: String,
    val displayName: String,
    val shortDescription: String?,
    val description: String?,
    val hsCode: String?,
    val madeIn: String?,
    val clientProductCode: String?,
    val quantity: BigDecimal,
    val unit: String?,
    val unitPriceExclTax: BigDecimal,
    val vatRate: BigDecimal,
    val lineTotalExclTax: BigDecimal,
  ) {
    companion object {
      fun from(line: DocumentLine, productService: ProductService): PdfLine {
        val product = line.product?.id?.let { productService.findDetailedById(it).orElse(null) }
        return PdfLine(
          designation = line.designation,
          displayName = product?.label?.takeIf { it.isNotBlank() } ?: line.designation,
          shortDescription = product?.shortDescription,
          description = line.description ?: product?.specifications,
          hsCode = line.hsCode ?: product?.hsCode,
          madeIn = line.madeIn ?: product?.madeIn,
          clientProductCode = line.clientProductCode,
          quantity = line.quantity,
          unit = line.unit,
          unitPriceExclTax = line.unitPriceExclTax,
          vatRate = line.vatRate,
          lineTotalExclTax = line.lineTotalExclTax,
        )
      }
    }
  }

  private fun incotermText(incoterm: String?, location: String?): String =
    listOfNotNull(incoterm, location?.takeIf { it.isNotBlank() }).joinToString(" ")

  /** Returns the lot value when it fits on a single non-blank line, else null. */
  private fun lotInline(lot: String?): String? =
    lot?.takeIf { it.isNotBlank() && !it.contains('\n') }

  /** Returns the lot value when it spans multiple lines, else null. */
  private fun lotMultiline(lot: String?): String? = lot?.takeIf { it.contains('\n') }

  private fun currencySymbol(currency: String): String =
    when (currency.uppercase()) {
      "EUR" -> "€"
      "USD" -> "$"
      "GBP" -> "£"
      "CNY",
      "RMB" -> "¥"
      else -> currency
    }

  /**
   * Uses the configured own-company logo first, then falls back to the legacy classpath Netstone
   * logo when present.
   */
  private fun logoSrc(ownCompany: Client?): String? =
    ownCompany?.logoData?.let { normalizeLogoDataUri(it) }
      ?: logoBase64()
      ?: generatedLogo(ownCompany?.name ?: "Netstone")

  /**
   * OpenHTMLToPDF reliably renders PNG/JPEG data URIs. Browser-only formats or incorrect upload
   * content types are ignored so the PDF does not keep a broken image reference.
   */
  private fun normalizeLogoDataUri(logoData: String): String? {
    val marker = ";base64,"
    val markerIndex = logoData.indexOf(marker, ignoreCase = true)
    if (!logoData.startsWith("data:", ignoreCase = true) || markerIndex < 0) return null

    val declaredContentType =
      logoData.substringAfter("data:").substringBefore(";").lowercase(Locale.ROOT)
    val base64Data = logoData.substring(markerIndex + marker.length).filterNot { it.isWhitespace() }
    val bytes =
      try {
        Base64.getDecoder().decode(base64Data)
      } catch (_: IllegalArgumentException) {
        return null
      }

    val contentType =
      when {
        bytes.isPng() -> "image/png"
        bytes.isJpeg() -> "image/jpeg"
        declaredContentType == "image/png" || declaredContentType == "image/jpeg" ->
          declaredContentType
        declaredContentType == "image/jpg" -> "image/jpeg"
        else -> return null
      }
    return "data:$contentType;base64," + Base64.getEncoder().encodeToString(bytes)
  }

  /** Reads the legacy Netstone logo from the classpath and returns a base64 data URI. */
  private fun logoBase64(): String? =
    PdfService::class
      .java
      .classLoader
      .getResourceAsStream("static/images/logo-netstone.png")
      ?.readBytes()
      ?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) }

  /** Generates a visible PNG wordmark when no uploaded company logo is configured. */
  private fun generatedLogo(companyName: String): String {
    val width = 460
    val height = 170
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.color = Color(255, 255, 255, 0)
      graphics.fillRect(0, 0, width, height)
      graphics.color = Color(224, 112, 40)
      graphics.fillRect(0, 126, width, 10)
      graphics.color = Color(51, 51, 51)
      graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 58)
      graphics.drawString(companyName.uppercase(Locale.ROOT), 0, 92)
    } finally {
      graphics.dispose()
    }

    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
  }

  private fun ByteArray.isPng(): Boolean =
    size >= 8 &&
      this[0] == 0x89.toByte() &&
      this[1] == 0x50.toByte() &&
      this[2] == 0x4E.toByte() &&
      this[3] == 0x47.toByte() &&
      this[4] == 0x0D.toByte() &&
      this[5] == 0x0A.toByte() &&
      this[6] == 0x1A.toByte() &&
      this[7] == 0x0A.toByte()

  private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()
}
