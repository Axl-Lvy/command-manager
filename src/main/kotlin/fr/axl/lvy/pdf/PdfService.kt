package fr.axl.lvy.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.order.OrderNetstoneService
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/** Generates PDF documents from Thymeleaf templates for business objects. */
@Service
class PdfService(
  private val orderNetstoneService: OrderNetstoneService,
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
    val ownCompany = clientService.findDefaultCodigSupplier().orElse(null)
    val currency = order.orderCodig.currency

    val ctx = Context()
    ctx.setVariable("order", order)
    ctx.setVariable("lines", lines)
    ctx.setVariable("ownCompany", ownCompany)
    ctx.setVariable(
      "ownCompanyAddressLines",
      ownCompany?.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable(
      "supplierAddressLines",
      order.orderCodig.client.billingAddress?.lines() ?: emptyList<String>(),
    )
    ctx.setVariable("deliveryLocationLines", order.deliveryLocation?.lines() ?: emptyList<String>())
    ctx.setVariable("noteLines", order.notes?.lines() ?: emptyList<String>())
    ctx.setVariable("currencySymbol", currencySymbol(currency))
    ctx.setVariable("logoSrc", logoBase64())

    val html = templateEngine.process("order-netstone", ctx)

    val out = ByteArrayOutputStream()
    PdfRendererBuilder().withHtmlContent(html, null).toStream(out).run()
    return out.toByteArray()
  }

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
   * Reads the Netstone logo from the classpath and returns a base64 data URI, or null if the file
   * is not present.
   */
  private fun logoBase64(): String? =
    PdfService::class
      .java
      .classLoader
      .getResourceAsStream("static/images/logo-netstone.png")
      ?.readBytes()
      ?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) }
}
