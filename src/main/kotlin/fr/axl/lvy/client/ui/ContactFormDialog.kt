package fr.axl.lvy.client.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.contact.Contact
import java.util.function.Consumer

internal class ContactFormDialog(
  private val contact: Contact?,
  private val onSave: Consumer<Contact>,
) : Dialog() {

  private val lastName = TextField("Nom")
  private val firstName = TextField("Prénom")
  private val email = TextField("Email")
  private val phone = TextField("Téléphone")
  private val mobile = TextField("Mobile")
  private val jobTitle = TextField("Fonction")
  private val role = ComboBox<Contact.ContactRole>("Rôle")
  private val active = Checkbox("Actif")

  init {
    setHeaderTitle(if (contact == null) "Nouveau contact" else "Modifier contact")
    setWidth("500px")

    lastName.isRequired = true
    role.setItems(*Contact.ContactRole.entries.toTypedArray())
    role.setItemLabelGenerator {
      when (it) {
        Contact.ContactRole.PRIMARY -> "Principal"
        Contact.ContactRole.BILLING -> "Facturation"
        Contact.ContactRole.TECHNICAL -> "Technique"
        Contact.ContactRole.OTHER -> "Autre"
      }
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(lastName, firstName)
    form.add(email, phone)
    form.add(mobile, jobTitle)
    form.add(role, active)
    add(form)

    val saveBtn = Button("OK") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (contact != null) {
      populateForm(contact)
    } else {
      role.value = Contact.ContactRole.OTHER
      active.value = true
    }
  }

  private fun populateForm(c: Contact) {
    lastName.value = c.lastName
    firstName.value = c.firstName ?: ""
    email.value = c.email ?: ""
    phone.value = c.phone ?: ""
    mobile.value = c.mobile ?: ""
    jobTitle.value = c.jobTitle ?: ""
    role.value = c.role
    active.value = c.active
  }

  private fun save() {
    if (lastName.isEmpty) {
      Notification.show("Le nom est obligatoire", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val c = contact ?: Contact(lastName.value)
    if (contact != null) {
      c.lastName = lastName.value
    }
    c.firstName = if (firstName.value.isBlank()) null else firstName.value
    c.email = if (email.value.isBlank()) null else email.value
    c.phone = if (phone.value.isBlank()) null else phone.value
    c.mobile = if (mobile.value.isBlank()) null else mobile.value
    c.jobTitle = if (jobTitle.value.isBlank()) null else jobTitle.value
    c.role = role.value
    c.active = active.value

    onSave.accept(c)
    close()
  }
}
