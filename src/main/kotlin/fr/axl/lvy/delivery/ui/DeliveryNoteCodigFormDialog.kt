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
import fr.axl.lvy.delivery.DeliveryNoteCodig
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.DeliveryNoteNetstone
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.pdf.PdfService

internal class DeliveryNoteCodigFormDialog(
  private val deliveryNoteCodigService: DeliveryNoteCodigService,
  private val pdfService: PdfService,
  private val orderCodig: OrderCodig,
  private val saleNumber: String,
  private val clientReference: String?,
  private val netstoneDeliveryNote: DeliveryNoteNetstone?,
  private val note: DeliveryNoteCodig?,
  private val onSave: Runnable,
) : Dialog() {

  private val noteNumber = TextField("N° Livraison")
  private val saleDocumentNumber = TextField("Vente CoDIG")
  private val customerReference = TextField("Réf. client")
  private val shippingAddress = TextArea("Adresse livraison")
  private val arrivalDate = DatePicker("Date arrivée")
  private val containerNumber = TextField("N° conteneur (séparés par une virgule si plusieurs)")
  private val billOfLading = TextField("BL")
  private val lot = TextArea("Lots (séparés par une virgule si plusieurs)")
  private val seals = TextField("Scellés")
  private val observations = TextArea("Observations")

  init {
    headerTitle = if (note == null) "Nouvelle livraison" else "Modifier livraison"
    width = "760px"

    noteNumber.isReadOnly = true
    saleDocumentNumber.isReadOnly = true
    saleDocumentNumber.value = saleNumber
    customerReference.isReadOnly = true
    customerReference.value = clientReference ?: ""

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(noteNumber, saleDocumentNumber)
    form.add(customerReference, 2)
    form.add(shippingAddress, 2)
    form.add(arrivalDate, containerNumber)
    form.add(billOfLading, 2)
    form.add(lot, 2)
    form.add(seals)
    form.add(observations, 2)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val footerLayout = HorizontalLayout(saveBtn, cancelBtn)
    if (note?.id != null) {
      val pdfResource =
        StreamResource("${note.deliveryNoteNumber.replace("/", "_")}.pdf") {
            pdfService.generateDeliveryCodigPdf(note.id!!).inputStream()
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
      noteNumber.value = "(auto)"
      shippingAddress.value = orderCodig.shippingAddress ?: ""
      populateLogisticsFromNetstone()
    }
  }

  private fun populateForm(note: DeliveryNoteCodig) {
    noteNumber.value = note.deliveryNoteNumber
    shippingAddress.value = note.shippingAddress ?: ""
    arrivalDate.value = note.arrivalDate
    containerNumber.value = note.containerNumber ?: ""
    billOfLading.value = note.billOfLading ?: ""
    lot.value = note.lot ?: ""
    seals.value = note.seals ?: ""
    observations.value = note.observations ?: ""
    populateMissingLogisticsFromNetstone(note)
  }

  private fun populateLogisticsFromNetstone() {
    arrivalDate.value = netstoneDeliveryNote?.arrivalDate
    containerNumber.value = netstoneDeliveryNote?.containerNumber ?: ""
    billOfLading.value = netstoneDeliveryNote?.billOfLading ?: ""
    lot.value = netstoneDeliveryNote?.lot ?: ""
    seals.value = netstoneDeliveryNote?.seals ?: ""
  }

  private fun populateMissingLogisticsFromNetstone(note: DeliveryNoteCodig) {
    if (note.arrivalDate == null) arrivalDate.value = netstoneDeliveryNote?.arrivalDate
    if (note.containerNumber.isNullOrBlank()) {
      containerNumber.value = netstoneDeliveryNote?.containerNumber ?: ""
    }
    if (note.billOfLading.isNullOrBlank()) {
      billOfLading.value = netstoneDeliveryNote?.billOfLading ?: ""
    }
    if (note.lot.isNullOrBlank()) lot.value = netstoneDeliveryNote?.lot ?: ""
    if (note.seals.isNullOrBlank()) seals.value = netstoneDeliveryNote?.seals ?: ""
  }

  private fun save() {
    val deliveryNote = note ?: DeliveryNoteCodig("", orderCodig, orderCodig.client)
    deliveryNote.shippingAddress = shippingAddress.value.takeIf { it.isNotBlank() }
    deliveryNote.arrivalDate = arrivalDate.value
    deliveryNote.containerNumber = containerNumber.value.takeIf { it.isNotBlank() }
    deliveryNote.billOfLading = billOfLading.value.takeIf { it.isNotBlank() }
    deliveryNote.lot = lot.value.takeIf { it.isNotBlank() }
    deliveryNote.seals = seals.value.takeIf { it.isNotBlank() }
    deliveryNote.observations = observations.value.takeIf { it.isNotBlank() }

    val saved = deliveryNoteCodigService.save(deliveryNote)
    noteNumber.value = saved.deliveryNoteNumber

    Notification.show("Livraison enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
