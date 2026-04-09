package fr.axl.lvy.delivery.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.delivery.DeliveryNoteA
import fr.axl.lvy.delivery.DeliveryNoteAService
import fr.axl.lvy.order.OrderA

internal class DeliveryNoteAFormDialog(
  private val deliveryNoteAService: DeliveryNoteAService,
  private val orderA: OrderA,
  private val note: DeliveryNoteA?,
  private val onSave: Runnable,
) : Dialog() {

  private val noteNumber = TextField("N° Livraison")
  private val status = ComboBox<DeliveryNoteA.DeliveryNoteAStatus>("Statut")
  private val shippingDate = DatePicker("Date expédition")
  private val deliveryDate = DatePicker("Date livraison")
  private val shippingAddress = TextArea("Adresse livraison")
  private val carrier = TextField("Transporteur")
  private val trackingNumber = TextField("N° suivi")
  private val packageCount = IntegerField("Nombre de colis")
  private val signedBy = TextField("Signé par")
  private val signatureDate = DatePicker("Date signature")
  private val observations = TextArea("Observations")

  init {
    headerTitle = if (note == null) "Nouvelle livraison" else "Modifier livraison"
    width = "760px"

    noteNumber.isReadOnly = true
    status.setItems(*DeliveryNoteA.DeliveryNoteAStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        DeliveryNoteA.DeliveryNoteAStatus.PREPARED -> "Préparée"
        DeliveryNoteA.DeliveryNoteAStatus.SHIPPED -> "Expédiée"
        DeliveryNoteA.DeliveryNoteAStatus.DELIVERED -> "Livrée"
        DeliveryNoteA.DeliveryNoteAStatus.INCIDENT -> "Incident"
      }
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(noteNumber, status)
    form.add(shippingDate, deliveryDate)
    form.add(carrier, trackingNumber)
    form.add(packageCount, signedBy)
    form.add(signatureDate)
    form.add(shippingAddress, 2)
    form.add(observations, 2)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (note != null) {
      populateForm(note)
    } else {
      noteNumber.value = "(auto)"
      status.value = DeliveryNoteA.DeliveryNoteAStatus.PREPARED
      shippingAddress.value = orderA.shippingAddress ?: ""
    }
  }

  private fun populateForm(note: DeliveryNoteA) {
    noteNumber.value = note.deliveryNoteNumber
    status.value = note.status
    shippingDate.value = note.shippingDate
    deliveryDate.value = note.deliveryDate
    shippingAddress.value = note.shippingAddress ?: ""
    carrier.value = note.carrier ?: ""
    trackingNumber.value = note.trackingNumber ?: ""
    packageCount.value = note.packageCount
    signedBy.value = note.signedBy ?: ""
    signatureDate.value = note.signatureDate
    observations.value = note.observations ?: ""
  }

  private fun save() {
    val deliveryNote = note ?: DeliveryNoteA("", orderA, orderA.client)
    deliveryNote.status = status.value ?: DeliveryNoteA.DeliveryNoteAStatus.PREPARED
    deliveryNote.shippingDate = shippingDate.value
    deliveryNote.deliveryDate = deliveryDate.value
    deliveryNote.shippingAddress = shippingAddress.value.takeIf { it.isNotBlank() }
    deliveryNote.carrier = carrier.value.takeIf { it.isNotBlank() }
    deliveryNote.trackingNumber = trackingNumber.value.takeIf { it.isNotBlank() }
    deliveryNote.packageCount = packageCount.value
    deliveryNote.signedBy = signedBy.value.takeIf { it.isNotBlank() }
    deliveryNote.signatureDate = signatureDate.value
    deliveryNote.observations = observations.value.takeIf { it.isNotBlank() }

    val saved = deliveryNoteAService.save(deliveryNote)
    noteNumber.value = saved.deliveryNoteNumber

    Notification.show("Livraison enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
