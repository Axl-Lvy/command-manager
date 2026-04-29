package fr.axl.lvy.invoice

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.sale.SalesCodig
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDate
import java.util.Optional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages Codig invoices created from customer orders and their invoice lines. */
@Service
class InvoiceCodigService(
  private val invoiceCodigRepository: InvoiceCodigRepository,
  private val orderCodigRepository: OrderCodigRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val clientService: ClientService,
  meterRegistry: MeterRegistry,
) {
  private val invoicesCreatedCounter = meterRegistry.counter("invoice.codig")

  companion object {
    private val log = LoggerFactory.getLogger(InvoiceCodigService::class.java)
  }

  /** Returns the active invoice already linked to the given Codig order, if any. */
  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): InvoiceCodig? =
    invoiceCodigRepository.findDetailedByOrderCodigId(orderCodigId)

  /** Loads the invoice lines persisted for the given invoice. */
  @Transactional(readOnly = true)
  fun findLines(invoiceId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.INVOICE_CODIG, invoiceId)

  /** Loads a single invoice by id. */
  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<InvoiceCodig> =
    Optional.ofNullable(invoiceCodigRepository.findDetailedById(id))

  /** Returns the preview number shown before a Codig invoice is first saved. */
  @Transactional(readOnly = true)
  fun previewNextInvoiceNumber(invoiceDate: LocalDate): String =
    numberSequenceService.previewNextNumberForYear(
      NumberSequenceService.INVOICE_CODIG,
      invoiceDate.year,
    )

  /** Prepares an existing invoice or a prefilled draft invoice for the given Codig sale. */
  @Transactional(readOnly = true)
  fun prepareForSale(sale: SalesCodig, order: OrderCodig): InvoiceCodig {
    val ownCompanyNotes = clientService.findDefaultCodigCompany().orElse(null)?.notes
    return order.id?.let(::findByOrderCodigId)
      ?: order.invoice
      ?: InvoiceCodig("", sale.client, sale.saleDate).apply {
        orderCodig = order
        deliveryNote = order.deliveryNote
        client = sale.client
        clientName = sale.client.name
        clientAddress = sale.billingAddress ?: sale.client.billingAddress
        clientSiret = sale.client.siret
        clientVatNumber = sale.client.vatNumber
        dueDate = sale.expectedDeliveryDate
        currency = sale.currency
        incoterms = sale.incoterms
        notes = sale.notes?.takeIf { it.isNotBlank() } ?: ownCompanyNotes
      }
  }

  /** Saves the invoice and replaces its lines from the current editor content. */
  @Transactional
  fun saveWithLines(invoice: InvoiceCodig, lines: List<DocumentLine>): InvoiceCodig {
    val isNew = invoice.invoiceNumber.isBlank()
    if (isNew) {
      invoice.invoiceNumber =
        numberSequenceService.nextNumberForYear(
          NumberSequenceService.INVOICE_CODIG,
          invoice.invoiceDate.year,
        )
    }
    if (invoice.orderCodig == null) {
      error("La facture CoDIG doit être rattachée à une commande CoDIG")
    }
    if (invoice.clientName.isNullOrBlank()) {
      invoice.clientName = invoice.client.name
    }
    if (invoice.clientAddress.isNullOrBlank()) {
      invoice.clientAddress = invoice.orderCodig?.billingAddress ?: invoice.client.billingAddress
    }
    if (invoice.clientSiret.isNullOrBlank()) {
      invoice.clientSiret = invoice.client.siret
    }
    if (invoice.clientVatNumber.isNullOrBlank()) {
      invoice.clientVatNumber = invoice.client.vatNumber
    }
    if (invoice.deliveryNote == null) {
      invoice.deliveryNote = invoice.orderCodig?.deliveryNote
    }

    val saved = invoiceCodigRepository.save(invoice)
    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.INVOICE_CODIG, saved.id!!, lines)
    saved.recalculateTotals(persistedLines)
    val persistedInvoice = invoiceCodigRepository.save(saved)

    val order = persistedInvoice.orderCodig ?: error("Commande CoDIG introuvable")
    if (order.invoice?.id != persistedInvoice.id) {
      order.invoice = persistedInvoice
    }
    // Only advance the order to INVOICED once the invoice leaves the DRAFT state.
    if (
      persistedInvoice.status != InvoiceCodig.InvoiceCodigStatus.DRAFT &&
        order.status != OrderCodig.OrderCodigStatus.INVOICED
    ) {
      order.status = OrderCodig.OrderCodigStatus.INVOICED
    }
    orderCodigRepository.save(order)

    if (isNew) {
      invoicesCreatedCounter.increment()
      log.info(
        "InvoiceCodig created: number={} order={}",
        persistedInvoice.invoiceNumber,
        order.orderNumber,
      )
    }
    return persistedInvoice
  }
}
