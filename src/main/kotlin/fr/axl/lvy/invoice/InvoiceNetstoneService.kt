package fr.axl.lvy.invoice

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneRepository
import fr.axl.lvy.sale.SalesNetstone
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDate
import java.util.Optional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages Netstone supplier invoices linked to procurement orders and their lines. */
@Service
class InvoiceNetstoneService(
  private val invoiceNetstoneRepository: InvoiceNetstoneRepository,
  private val orderNetstoneRepository: OrderNetstoneRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val clientService: ClientService,
  meterRegistry: MeterRegistry,
) {
  private val invoicesCreatedCounter = meterRegistry.counter("invoice.netstone")

  companion object {
    private val log = LoggerFactory.getLogger(InvoiceNetstoneService::class.java)
  }

  /** Returns the active invoice already linked to the given Netstone order, if any. */
  @Transactional(readOnly = true)
  fun findByOrderNetstoneId(orderNetstoneId: Long): InvoiceNetstone? =
    invoiceNetstoneRepository.findDetailedByOrderNetstoneId(orderNetstoneId)

  /** Loads the invoice lines persisted for the given invoice. */
  @Transactional(readOnly = true)
  fun findLines(invoiceId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.INVOICE_NETSTONE, invoiceId)

  /** Loads a single invoice by id. */
  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<InvoiceNetstone> =
    Optional.ofNullable(invoiceNetstoneRepository.findDetailedById(id))

  /** Returns the preview number shown before a Netstone invoice is first saved. */
  @Transactional(readOnly = true)
  fun previewNextInvoiceNumber(invoiceDate: LocalDate): String =
    numberSequenceService.previewNextNumber(sequenceKey(invoiceDate), previewPrefix(invoiceDate), 3)

  /** Prepares an existing invoice or a prefilled draft invoice for the given Netstone sale. */
  @Transactional(readOnly = true)
  fun prepareForSale(sale: SalesNetstone, order: OrderNetstone): InvoiceNetstone =
    order.id?.let(::findByOrderNetstoneId)
      ?: order.invoiceNetstone
      ?: createDraftInvoice(sale, order)

  private fun createDraftInvoice(sale: SalesNetstone, order: OrderNetstone): InvoiceNetstone {
    val codig =
      clientService.findDefaultCodigCompany().orElseThrow {
        IllegalStateException("Aucune societe Codig n'est configuree")
      }
    return InvoiceNetstone(
        "",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        codig,
        sale.saleDate ?: order.orderDate ?: order.orderCodig.orderDate,
      )
      .apply {
        recipient = codig
        billingAddress = codig.billingAddress
        orderNetstone = order
        origin = InvoiceNetstone.Origin.ORDER_LINKED
        dueDate = sale.expectedDeliveryDate
        notes = sale.notes
      }
  }

  /** Saves the invoice and replaces its lines from the current editor content. */
  @Transactional
  fun saveWithLines(invoice: InvoiceNetstone, lines: List<DocumentLine>): InvoiceNetstone {
    val isNew = invoice.internalInvoiceNumber.isBlank()
    if (isNew) {
      invoice.internalInvoiceNumber =
        numberSequenceService.nextNumber(
          sequenceKey(invoice.invoiceDate),
          previewPrefix(invoice.invoiceDate),
          3,
        )
    }
    if (invoice.orderNetstone == null) {
      error("La facture Netstone doit etre rattachee a une commande Netstone")
    }
    invoice.origin = InvoiceNetstone.Origin.ORDER_LINKED
    if (invoice.recipientType == InvoiceNetstone.RecipientType.COMPANY_CODIG) {
      val codig =
        clientService.findDefaultCodigCompany().orElseThrow {
          IllegalStateException("Aucune societe Codig n'est configuree")
        }
      invoice.recipient = codig
      if (invoice.billingAddress.isNullOrBlank()) {
        invoice.billingAddress = codig.billingAddress
      }
    }

    val saved = invoiceNetstoneRepository.save(invoice)
    val persistedLines =
      documentLineService.replaceLines(DocumentLine.DocumentType.INVOICE_NETSTONE, saved.id!!, lines)
    saved.recalculateTotals(persistedLines)
    val persistedInvoice = invoiceNetstoneRepository.save(saved)

    val order = persistedInvoice.orderNetstone ?: error("Commande Netstone introuvable")
    if (order.invoiceNetstone?.id != persistedInvoice.id) {
      order.invoiceNetstone = persistedInvoice
      orderNetstoneRepository.save(order)
    }

    if (isNew) {
      invoicesCreatedCounter.increment()
      log.info(
        "InvoiceNetstone created: number={} order={}",
        persistedInvoice.internalInvoiceNumber,
        order.orderNumber,
      )
    }
    return persistedInvoice
  }

  private fun sequenceKey(invoiceDate: LocalDate): String =
    "${NumberSequenceService.INVOICE_NETSTONE}_${invoiceDate.year}"

  private fun previewPrefix(invoiceDate: LocalDate): String = "NST_INV/${invoiceDate.year}/"
}
