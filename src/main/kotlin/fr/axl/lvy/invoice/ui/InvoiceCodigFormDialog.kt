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
import com.vaadin.flow.server.StreamResource
import fr.axl.lvy.base.ui.noGap
import fr.axl.lvy.delivery.DeliveryNoteNetstone
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.invoice.InvoiceCodig
import fr.axl.lvy.invoice.InvoiceCodigService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig

/**
 * Dialog used to create or edit a [InvoiceCodig] from a [SalesCodig]. The form is read-only for the
 * sale-derived fields (client, references, terms) and lets the user adjust invoice metadata and
 * lines before saving.
 */
internal class InvoiceCodigFormDialog(
  private val invoiceCodigService: InvoiceCodigService,
  private val productService: ProductService,
  private val pdfService: PdfService,
  private val sale: SalesCodig,
  private val orderCodig: OrderCodig,
  private val invoice: InvoiceCodig,
  private val netstoneDeliveryNote: DeliveryNoteNetstone?,
  saleLines: List<DocumentLine>,
  initialLines: List<DocumentLine>,
  private val onSave: Runnable,
) : Dialog() {

  private val invoiceNumber = TextField("N° Facture")
  private val saleNumber = TextField("Vente CoDIG")
  private val status = ComboBox<InvoiceCodig.InvoiceCodigStatus>("Statut")
  private val invoiceDate = DatePicker("Date facture")
  private val dueDate = DatePicker("Échéance")
  private val clientName = TextField("Client")
  private val clientReference = TextField("Réf. client")
  private val paymentTerm = TextField("Condition de paiement")
  private val fiscalPosition = TextField("Position fiscale")
  private val incoterm = TextField("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val billOfLading = TextField("BL")
  private val containerNumber = TextField("N° de conteneur")
  private val seals = TextField("Seals")
  private val clientAddress = TextArea("Adresse facturation")
  private val notes = TextArea("Notes")
  private val lineEditor =
    DocumentLineEditor(
      productService = productService,
      documentType = DocumentLine.DocumentType.INVOICE_CODIG,
      clientSupplier = { sale.client },
      currencySupplier = { invoice.currency },
      unitPriceOverrideProvider = { product ->
        saleLines.firstOrNull { it.product?.id == product.id }?.unitPriceExclTax
      },
      lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
    )

  init {
    headerTitle =
      if (invoice.id == null) "Nouvelle facture CoDIG" else "Facture CoDIG ${invoice.invoiceNumber}"
    width = "960px"
    height = "85%"

    invoiceNumber.isReadOnly = true
    saleNumber.isReadOnly = true
    saleNumber.value = sale.saleNumber
    status.setItems(*InvoiceCodig.InvoiceCodigStatus.entries.toTypedArray())
    status.setItemLabelGenerator(::statusLabel)
    clientName.isReadOnly = true
    clientReference.isReadOnly = true
    paymentTerm.isReadOnly = true
    fiscalPosition.isReadOnly = true
    incoterm.isReadOnly = true
    incotermLocation.isReadOnly = true
    billOfLading.isReadOnly = true
    containerNumber.isReadOnly = true
    seals.isReadOnly = true
    invoiceDate.addValueChangeListener {
      if (invoice.id == null) {
        invoiceNumber.value =
          invoiceCodigService.previewNextInvoiceNumber(it.value ?: sale.saleDate)
      }
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(invoiceNumber, saleNumber)
    form.add(status, invoiceDate)
    form.add(dueDate, 2)
    form.add(clientName, 2)
    form.add(clientReference, paymentTerm)
    form.add(fiscalPosition, incoterm)
    form.add(incotermLocation, billOfLading)
    form.add(containerNumber, seals)
    form.add(clientAddress, 2)
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
      val pdfResource =
        StreamResource("${invoice.invoiceNumber.replace("/", "_")}.pdf") {
            pdfService.generateInvoiceCodigPdf(invoice.id!!).inputStream()
          }
          .apply { cacheTime = 0 }
      val pdfBtn = Button("Télécharger PDF")
      val pdfLink =
        Anchor(pdfResource, "").apply {
          element.setAttribute("download", true)
          add(pdfBtn)
        }
      footerLayout.add(pdfLink)
    }
    footer.add(footerLayout)
  }

  private fun populateForm(invoice: InvoiceCodig) {
    invoiceNumber.value =
      invoice.invoiceNumber.ifBlank {
        invoiceCodigService.previewNextInvoiceNumber(invoice.invoiceDate)
      }
    invoiceDate.value = invoice.invoiceDate
    status.value = invoice.status
    dueDate.value = invoice.dueDate
    clientName.value = sale.client.name
    clientReference.value = sale.clientReference ?: ""
    paymentTerm.value = sale.paymentTerm?.label ?: ""
    fiscalPosition.value = sale.fiscalPosition?.position ?: ""
    incoterm.value = sale.incoterms ?: ""
    incotermLocation.value = sale.incotermLocation ?: ""
    billOfLading.value = netstoneDeliveryNote?.billOfLading ?: ""
    containerNumber.value = netstoneDeliveryNote?.containerNumber ?: ""
    seals.value = netstoneDeliveryNote?.seals ?: ""
    clientAddress.value =
      invoice.clientAddress ?: sale.billingAddress ?: sale.client.billingAddress ?: ""
    notes.value = invoice.notes ?: ""
  }

  private fun save() {
    invoice.orderCodig = orderCodig
    invoice.deliveryNote = orderCodig.deliveryNote
    invoice.client = sale.client
    invoice.status = status.value ?: InvoiceCodig.InvoiceCodigStatus.DRAFT
    invoice.invoiceDate = invoiceDate.value ?: sale.saleDate
    invoice.dueDate = dueDate.value
    invoice.clientName = sale.client.name
    invoice.clientAddress =
      clientAddress.value.takeIf { it.isNotBlank() }
        ?: sale.billingAddress
        ?: sale.client.billingAddress
    invoice.clientSiret = sale.client.siret
    invoice.clientVatNumber = sale.client.vatNumber
    invoice.currency = sale.currency
    invoice.incoterms = sale.incoterms
    invoice.notes = notes.value.takeIf { it.isNotBlank() }

    val saved = invoiceCodigService.saveWithLines(invoice, lineEditor.getLines())
    invoiceNumber.value = saved.invoiceNumber
    Notification.show("Facture CoDIG enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }

  private fun statusLabel(status: InvoiceCodig.InvoiceCodigStatus): String =
    when (status) {
      InvoiceCodig.InvoiceCodigStatus.DRAFT -> "Brouillon"
      InvoiceCodig.InvoiceCodigStatus.ISSUED -> "Émise"
      InvoiceCodig.InvoiceCodigStatus.OVERDUE -> "En retard"
      InvoiceCodig.InvoiceCodigStatus.PAID -> "Payée"
      InvoiceCodig.InvoiceCodigStatus.CANCELLED -> "Annulée"
      InvoiceCodig.InvoiceCodigStatus.CREDIT_NOTE -> "Avoir"
    }
}
