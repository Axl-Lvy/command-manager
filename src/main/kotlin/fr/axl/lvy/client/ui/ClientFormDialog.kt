package fr.axl.lvy.client.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.user.User

internal class ClientFormDialog(
  private val clientService: ClientService,
  private val client: Client?,
  private val onSave: Runnable,
  private val mode: ClientFormMode = ClientFormMode.CLIENT,
) : Dialog() {

  private val name = TextField("Nom")
  private val type = ComboBox<Client.ClientType>("Type")
  private val role = ComboBox<Client.ClientRole>("Rôle")
  private val visibleCompany = ComboBox<User.Company>("Société visible")
  private val email = TextField("Email")
  private val phone = TextField("Téléphone")
  private val website = TextField("Site web")
  private val siret = TextField("SIRET")
  private val vatNumber = TextField("N° TVA intra.")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val paymentDelay = IntegerField("Délai paiement (jours)")
  private val paymentMethod = TextField("Mode de paiement")
  private val statusToggle = Button()
  private val notes = TextArea("Notes")

  private val contacts = mutableListOf<Contact>()
  private val contactGrid = Grid<Contact>()

  init {
    setHeaderTitle(
      if (client == null) {
        if (mode == ClientFormMode.OWN_COMPANY) "Nouvelle société" else "Nouveau client"
      } else {
        if (mode == ClientFormMode.OWN_COMPANY) "Modifier société" else "Modifier client"
      }
    )
    setWidth("750px")
    setHeight("80%")

    type.setItems(*Client.ClientType.entries.toTypedArray())
    type.setItemLabelGenerator {
      when (it) {
        Client.ClientType.COMPANY -> "Société"
        Client.ClientType.INDIVIDUAL -> "Particulier"
        Client.ClientType.OWN_COMPANY -> "Mes sociétés"
      }
    }
    role.setItems(*Client.ClientRole.entries.toTypedArray())
    role.setItemLabelGenerator {
      when (it) {
        Client.ClientRole.CLIENT -> "Client"
        Client.ClientRole.PRODUCER -> "Producteur"
        Client.ClientRole.BOTH -> "Les deux"
        Client.ClientRole.OWN_COMPANY -> "Ma société"
      }
    }
    visibleCompany.setItems(*User.Company.entries.toTypedArray())
    visibleCompany.setItemLabelGenerator {
      when (it) {
        User.Company.A -> "Société A"
        User.Company.B -> "Société B"
        User.Company.AB -> "Tout"
      }
    }
    if (mode == ClientFormMode.OWN_COMPANY) {
      type.isVisible = false
      visibleCompany.isVisible = false
    }
    statusToggle.addClickListener {
      val currentStatus = statusToggle.element.getProperty("data-status")
      val nextStatus =
        if (currentStatus == Client.Status.ACTIVE.name) Client.Status.INACTIVE
        else Client.Status.ACTIVE
      updateStatusButton(nextStatus)
    }

    name.isRequired = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    if (mode == ClientFormMode.OWN_COMPANY) {
      form.add(name, role)
    } else {
      form.add(name, type)
    }
    if (mode != ClientFormMode.OWN_COMPANY) {
      form.add(role, visibleCompany)
    }
    form.add(statusToggle, email)
    form.add(phone, website)
    form.add(siret, vatNumber)
    form.add(paymentMethod, paymentDelay)
    form.add(billingAddress, 2)
    form.add(shippingAddress, 2)
    form.add(notes, 2)

    contactGrid.addColumn(Contact::lastName).setHeader("Nom").setFlexGrow(1)
    contactGrid.addColumn(Contact::firstName).setHeader("Prénom").setAutoWidth(true)
    contactGrid.addColumn(Contact::email).setHeader("Email").setAutoWidth(true)
    contactGrid.addColumn(Contact::phone).setHeader("Téléphone").setAutoWidth(true)
    contactGrid.addColumn { it.role.name }.setHeader("Rôle").setAutoWidth(true)
    contactGrid.setHeight("200px")

    val addContactBtn = Button("Ajouter contact") { addContact() }
    val removeContactBtn = Button("Supprimer") { removeSelectedContact() }
    removeContactBtn.addThemeVariants(ButtonVariant.LUMO_ERROR)

    val contactActions = HorizontalLayout(addContactBtn, removeContactBtn)
    val contactSection = VerticalLayout(H3("Contacts"), contactActions, contactGrid)
    contactSection.isPadding = false
    contactSection.isSpacing = false

    val content = VerticalLayout(form, contactSection)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (client != null) {
      populateForm(client)
    } else {
      type.value =
        if (mode == ClientFormMode.OWN_COMPANY) Client.ClientType.OWN_COMPANY
        else Client.ClientType.COMPANY
      role.value =
        if (mode == ClientFormMode.OWN_COMPANY) Client.ClientRole.OWN_COMPANY
        else Client.ClientRole.CLIENT
      visibleCompany.value = User.Company.AB
      updateStatusButton(Client.Status.ACTIVE)
    }
  }

  private fun populateForm(c: Client) {
    name.value = c.name
    type.value = if (mode == ClientFormMode.OWN_COMPANY) Client.ClientType.OWN_COMPANY else c.type
    role.value = c.role
    visibleCompany.value = c.visibleCompany
    email.value = c.email ?: ""
    phone.value = c.phone ?: ""
    website.value = c.website ?: ""
    siret.value = c.siret ?: ""
    vatNumber.value = c.vatNumber ?: ""
    billingAddress.value = c.billingAddress ?: ""
    shippingAddress.value = c.shippingAddress ?: ""
    paymentDelay.value = c.paymentDelay
    paymentMethod.value = c.paymentMethod ?: ""
    updateStatusButton(c.status)
    notes.value = c.notes ?: ""
    contacts.addAll(c.contacts)
    contactGrid.setItems(contacts)
  }

  private fun addContact() {
    ContactFormDialog(null) { contact ->
        contacts.add(contact)
        contactGrid.setItems(contacts)
      }
      .open()
  }

  private fun removeSelectedContact() {
    contactGrid.selectedItems.forEach { contacts.remove(it) }
    contactGrid.setItems(contacts)
  }

  private fun save() {
    if (name.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    try {
      val c = client ?: Client(name = name.value)
      if (client != null) {
        c.name = name.value
      } else {
        c.status = Client.Status.ACTIVE
      }
      c.type =
        if (mode == ClientFormMode.OWN_COMPANY) Client.ClientType.OWN_COMPANY
        else type.value ?: Client.ClientType.COMPANY
      c.role =
        role.value
          ?: if (mode == ClientFormMode.OWN_COMPANY) {
            Client.ClientRole.OWN_COMPANY
          } else {
            Client.ClientRole.CLIENT
          }
      c.visibleCompany =
        if (mode == ClientFormMode.OWN_COMPANY) {
          c.visibleCompany
        } else {
          visibleCompany.value ?: User.Company.AB
        }
      c.email = if (email.value.isBlank()) null else email.value
      c.phone = if (phone.value.isBlank()) null else phone.value
      c.website = if (website.value.isBlank()) null else website.value
      c.siret = if (siret.value.isBlank()) null else siret.value
      c.vatNumber = if (vatNumber.value.isBlank()) null else vatNumber.value
      c.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
      c.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
      c.paymentDelay = paymentDelay.value
      c.paymentMethod = if (paymentMethod.value.isBlank()) null else paymentMethod.value
      c.status =
        if (statusToggle.element.getProperty("data-status") == Client.Status.INACTIVE.name) {
          Client.Status.INACTIVE
        } else {
          Client.Status.ACTIVE
        }
      c.notes = if (notes.value.isBlank()) null else notes.value

      c.contacts.clear()
      for (contact in contacts) {
        contact.client = c
        c.contacts.add(contact)
      }

      clientService.save(c)
      Notification.show(
          if (mode == ClientFormMode.OWN_COMPANY) "Société enregistrée" else "Client enregistré",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
      onSave.run()
      close()
    } catch (e: Exception) {
      Notification.show(
          e.message ?: "Erreur lors de l'enregistrement",
          5000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
    }
  }

  private fun updateStatusButton(status: Client.Status) {
    statusToggle.text = if (status == Client.Status.ACTIVE) "Actif" else "Inactif"
    statusToggle.element.setProperty("data-status", status.name)
    statusToggle.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_CONTRAST)
    if (status == Client.Status.ACTIVE) {
      statusToggle.addThemeVariants(ButtonVariant.LUMO_SUCCESS)
    } else {
      statusToggle.addThemeVariants(ButtonVariant.LUMO_CONTRAST)
    }
  }

  enum class ClientFormMode {
    CLIENT,
    OWN_COMPANY,
  }
}
