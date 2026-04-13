package fr.axl.lvy.client.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import java.util.function.Consumer

/**
 * Dialog for creating or editing a [ClientDeliveryAddress]. Calls [onSave] with the populated
 * entity on confirm; the caller is responsible for adding or updating it in the parent list.
 */
internal class DeliveryAddressFormDialog(
  private val deliveryAddress: ClientDeliveryAddress?,
  private val onSave: Consumer<ClientDeliveryAddress>,
) : Dialog() {

  private val label = TextField("Libellé")
  private val address = TextArea("Adresse")
  private val defaultAddress = Checkbox("Adresse par défaut")

  init {
    setHeaderTitle(
      if (deliveryAddress == null) "Nouvelle adresse de livraison"
      else "Modifier adresse de livraison"
    )
    setWidth("550px")

    label.isRequired = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 1))
    form.add(label, address, defaultAddress)
    add(form)

    val saveBtn = Button("OK") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (deliveryAddress != null) {
      populateForm(deliveryAddress)
    }
  }

  private fun populateForm(deliveryAddress: ClientDeliveryAddress) {
    label.value = deliveryAddress.label
    address.value = deliveryAddress.address
    defaultAddress.value = deliveryAddress.defaultAddress
  }

  private fun save() {
    if (label.isEmpty) {
      Notification.show("Le libellé est obligatoire", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val entity = deliveryAddress ?: ClientDeliveryAddress(label.value)
    entity.label = label.value
    entity.address = address.value
    entity.defaultAddress = defaultAddress.value
    onSave.accept(entity)
    close()
  }
}
