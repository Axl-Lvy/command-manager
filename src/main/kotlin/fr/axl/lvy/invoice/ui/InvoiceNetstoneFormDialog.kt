package fr.axl.lvy.invoice.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.server.streams.DownloadHandler
import com.vaadin.flow.server.streams.DownloadResponse
import fr.axl.lvy.base.ui.noGap
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.invoice.InvoiceNetstone
import fr.axl.lvy.invoice.InvoiceNetstoneService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesNetstone
import java.io.ByteArrayInputStream

/**
 * Dialog used to create or edit an [InvoiceNetstone] from a [SalesNetstone]. The recipient is
 * resolved from the persisted invoice rather than hardcoded so external recipients are also
 * supported.
 */
internal class InvoiceNetstoneFormDialog(
  private val invoiceNetstoneService: InvoiceNetstoneService,
  private val productService: ProductService,
  private val pdfService: PdfService,
  private val sale: SalesNetstone,
  private val orderNetstone: OrderNetstone,
  private val invoice: InvoiceNetstone,
  saleLines: List<DocumentLine>,
  initialLines: List<DocumentLine>,
  private val onSave: Runnable,
) : Dialog() {

  private val invoiceNumber = TextField("N° Facture interne")
  private val supplierInvoiceNumber = TextField("N° Facture fournisseur")
  private val saleNumber = TextField("Vente Netstone")
  private val status = ComboBox<InvoiceNetstone.InvoiceNetstoneStatus>("Statut")
  private val invoiceDate = DatePicker("Date facture")
  private val dueDate = DatePicker("Échéance")
  private val clientName = TextField("Client")
  private val clientReference = TextField("Réf. client")
  private val paymentTerm = TextField("Condition de paiement")
  private val incoterm = TextField("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val billingAddress = TextArea("Adresse de facturation")
  private val notes = TextArea("Notes")
  private val lineEditor =
    DocumentLineEditor(
      productService = productService,
      documentType = DocumentLine.DocumentType.INVOICE_NETSTONE,
      clientSupplier = { sale.salesCodig.client },
      unitPriceOverrideProvider = { product ->
        saleLines.firstOrNull { it.product?.id == product.id }?.unitPriceExclTax
      },
      lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
    )

  init {
    headerTitle =
      if (invoice.id == null) "Nouvelle facture Netstone"
      else "Facture Netstone ${invoice.internalInvoiceNumber}"
    width = "960px"
    height = "85%"

    invoiceNumber.isReadOnly = true
    saleNumber.isReadOnly = true
    saleNumber.value = sale.saleNumber
    status.setItems(*InvoiceNetstone.InvoiceNetstoneStatus.entries.toTypedArray())
    status.setItemLabelGenerator(::statusLabel)
    clientName.isReadOnly = true
    clientReference.isReadOnly = true
    paymentTerm.isReadOnly = true
    incoterm.isReadOnly = true
    incotermLocation.isReadOnly = true
    invoiceDate.addValueChangeListener {
      if (invoice.id == null) {
        invoiceNumber.value =
          invoiceNetstoneService.previewNextInvoiceNumber(
            it.value ?: sale.saleDate ?: orderNetstone.orderCodig.orderDate
          )
      }
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(invoiceNumber, supplierInvoiceNumber)
    form.add(saleNumber, status)
    form.add(invoiceDate, dueDate)
    form.add(clientName, 2)
    form.add(clientReference, paymentTerm)
    form.add(incoterm, incotermLocation)
    form.add(billingAddress, 2)
    form.add(notes, 2)

    populateForm(invoice)
    lineEditor.setLines(initialLines)

    val content = VerticalLayout(form, lineEditor)
    content.noGap()
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val footerLayout = HorizontalLayout(saveBtn, cancelBtn)
    if (invoice.id != null) {
      val fileName = "${invoice.internalInvoiceNumber.replace("/", "_")}.pdf"
      val pdfHandler =
        DownloadHandler.fromInputStream {
          val bytes = pdfService.generateInvoiceNetstonePdf(invoice.id!!)
          DownloadResponse(ByteArrayInputStream(bytes), fileName, "application/pdf", bytes.size.toLong())
        }
      val pdfBtn = Button("Télécharger PDF")
      val pdfLink = Anchor(pdfHandler, "").apply { add(pdfBtn) }
      footerLayout.add(pdfLink)
    }
    footer.add(footerLayout)
  }

  private fun populateForm(invoice: InvoiceNetstone) {
    invoiceNumber.value =
      invoice.internalInvoiceNumber.ifBlank {
        invoiceNetstoneService.previewNextInvoiceNumber(invoice.invoiceDate)
      }
    supplierInvoiceNumber.value = invoice.supplierInvoiceNumber ?: ""
    invoiceDate.value = invoice.invoiceDate
    status.value = invoice.status
    dueDate.value = invoice.dueDate
    clientName.value = invoice.recipient.name
    clientReference.value = orderNetstone.orderCodig.orderNumber
    paymentTerm.value = sale.salesCodig.paymentTerm?.label ?: ""
    incoterm.value = sale.incoterms ?: ""
    incotermLocation.value = sale.incotermLocation ?: ""
    billingAddress.value = invoice.billingAddress ?: invoice.recipient.billingAddress ?: ""
    notes.value = invoice.notes ?: ""
  }

  private fun save() {
    invoice.orderNetstone = orderNetstone
    invoice.recipientType = InvoiceNetstone.RecipientType.COMPANY_CODIG
    invoice.status = status.value ?: InvoiceNetstone.InvoiceNetstoneStatus.RECEIVED
    invoice.invoiceDate = invoiceDate.value ?: sale.saleDate ?: orderNetstone.orderCodig.orderDate
    invoice.dueDate = dueDate.value
    invoice.billingAddress = billingAddress.value.takeIf { it.isNotBlank() }
    invoice.supplierInvoiceNumber = supplierInvoiceNumber.value.takeIf { it.isNotBlank() }
    invoice.notes = notes.value.takeIf { it.isNotBlank() }

    val saved = invoiceNetstoneService.saveWithLines(invoice, lineEditor.getLines())
    invoiceNumber.value = saved.internalInvoiceNumber
    Notification.show("Facture Netstone enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }

  private fun statusLabel(status: InvoiceNetstone.InvoiceNetstoneStatus): String =
    when (status) {
      InvoiceNetstone.InvoiceNetstoneStatus.RECEIVED -> "Reçue"
      InvoiceNetstone.InvoiceNetstoneStatus.VERIFIED -> "Vérifiée"
      InvoiceNetstone.InvoiceNetstoneStatus.PAID -> "Payée"
      InvoiceNetstone.InvoiceNetstoneStatus.DISPUTED -> "Litige"
    }
}
