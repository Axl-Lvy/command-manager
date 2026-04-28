package fr.axl.lvy.delivery.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
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
import fr.axl.lvy.delivery.DeliveryNoteNetstoneService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService

internal class DeliveryNoteNetstoneFormDialog(
  private val deliveryNoteNetstoneService: DeliveryNoteNetstoneService,
  private val productService: ProductService,
  private val pdfService: PdfService,
  private val orderNetstone: OrderNetstone,
  private val saleNumber: String,
  private val deliveryAddress: String?,
  private val note: DeliveryNoteNetstone?,
  initialLines: List<DocumentLine>,
  private val onSave: Runnable,
) : Dialog() {

  private val noteNumber = TextField("N° Livraison")
  private val saleDocumentNumber = TextField("Vente Netstone")
  private val shippingAddress = TextArea("Adresse livraison")
  private val arrivalDate = DatePicker("Date arrivée")
  private val containerNumber = TextField("N° conteneur (séparés par une virgule si plusieurs)")
  private val billOfLading = TextField("BL")
  private val lot = TextArea("Lots (séparés par une virgule si plusieurs)")
  private val seals = TextField("Scellés")
  private val observations = TextArea("Observations")
  private val lineEditor =
    DocumentLineEditor(
      productService = productService,
      documentType = DocumentLine.DocumentType.DELIVERY_NETSTONE,
      usePurchasePrice = true,
      lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
      showUnitPrice = false,
      showTax = false,
      showLineTotal = false,
    )

  init {
    headerTitle = if (note == null) "Nouvelle livraison Netstone" else "Modifier livraison Netstone"
    width = "960px"
    height = "80%"

    noteNumber.isReadOnly = true
    saleDocumentNumber.isReadOnly = true
    saleDocumentNumber.value = saleNumber
    shippingAddress.isReadOnly = true
    shippingAddress.value = deliveryAddress ?: ""

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(noteNumber, saleDocumentNumber)
    form.add(shippingAddress, 2)
    form.add(arrivalDate, containerNumber)
    form.add(billOfLading, 2)
    form.add(lot, 2)
    form.add(seals)
    form.add(observations, 2)

    lineEditor.setLines(initialLines)
    val content = VerticalLayout(form, lineEditor)
    content.noGap()
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val footerLayout = HorizontalLayout(saveBtn, cancelBtn)
    if (note?.id != null) {
      val pdfResource =
        StreamResource("${note.deliveryNoteNumber.replace("/", "_")}.pdf") {
            pdfService.generateDeliveryNetstonePdf(note.id!!).inputStream()
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

    if (note != null) {
      populateForm(note)
    } else {
      noteNumber.value = deliveryNoteNetstoneService.previewNextDeliveryNoteNumber()
    }
  }

  private fun populateForm(note: DeliveryNoteNetstone) {
    noteNumber.value = note.deliveryNoteNumber
    arrivalDate.value = note.arrivalDate
    containerNumber.value = note.containerNumber ?: ""
    billOfLading.value = note.billOfLading ?: ""
    lot.value = note.lot ?: ""
    seals.value = note.seals ?: ""
    observations.value = note.observations ?: ""
  }

  private fun save() {
    val deliveryNote = note ?: DeliveryNoteNetstone("", orderNetstone)
    deliveryNote.arrivalDate = arrivalDate.value
    deliveryNote.containerNumber = containerNumber.value.takeIf { it.isNotBlank() }
    deliveryNote.billOfLading = billOfLading.value.takeIf { it.isNotBlank() }
    deliveryNote.lot = lot.value.takeIf { it.isNotBlank() }
    deliveryNote.seals = seals.value.takeIf { it.isNotBlank() }
    deliveryNote.observations = observations.value.takeIf { it.isNotBlank() }

    val saved = deliveryNoteNetstoneService.saveWithLines(deliveryNote, lineEditor.getLines())
    noteNumber.value = saved.deliveryNoteNumber

    Notification.show("Livraison Netstone enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
