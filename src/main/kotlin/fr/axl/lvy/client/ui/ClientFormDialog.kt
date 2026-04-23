package fr.axl.lvy.client.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Image
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.upload.Upload
import com.vaadin.flow.server.streams.UploadHandler
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.user.User
import java.util.Base64

internal class ClientFormDialog(
  private val clientService: ClientService,
  paymentTermService: PaymentTermService,
  fiscalPositionService: FiscalPositionService,
  incotermService: IncotermService,
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
  private val paymentTerm = ComboBox<PaymentTerm>("Conditions de paiement")
  private val fiscalPosition = ComboBox<FiscalPosition>("Position fiscale")
  private val incoterm = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val deliveryPort = TextField("Port de livraison")
  private val logoUpload =
    Upload(
      UploadHandler.inMemory { metadata, bytes ->
        logoDataValue =
          "data:${metadata.contentType};base64," + Base64.getEncoder().encodeToString(bytes)
        updateLogoPreview()
      }
    )
  private val logoPreview = Image()
  private val clearLogoButton = Button("Supprimer logo")
  private val statusToggle = Button()
  private val notes = TextArea("Notes")

  private val contacts = mutableListOf<Contact>()
  private val deliveryAddresses = mutableListOf<ClientDeliveryAddress>()
  private val contactGrid = Grid<Contact>()
  private val deliveryAddressGrid = Grid<ClientDeliveryAddress>()
  private val availablePaymentTerms = paymentTermService.findAll()
  private val availableFiscalPositions = fiscalPositionService.findAll()
  private val availableIncoterms = incotermService.findAll()
  private var logoDataValue: String? = client?.logoData

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
        User.Company.CODIG -> "Société Codig"
        User.Company.NETSTONE -> "Société Netstone"
        User.Company.BOTH -> "Tout"
      }
    }
    if (mode == ClientFormMode.OWN_COMPANY) {
      type.isVisible = false
      visibleCompany.isVisible = false
    }
    statusToggle.addClickListener {
      val currentStatus = statusToggle.element.getProperty(DATA_STATUS_PROP)
      val nextStatus =
        if (currentStatus == Client.Status.ACTIVE.name) Client.Status.INACTIVE
        else Client.Status.ACTIVE
      updateStatusButton(nextStatus)
    }

    name.isRequired = true
    paymentTerm.setItems(availablePaymentTerms)
    paymentTerm.setItemLabelGenerator { it.label }
    fiscalPosition.setItems(availableFiscalPositions)
    fiscalPosition.setItemLabelGenerator { it.position }
    incoterm.setItems(availableIncoterms)
    incoterm.setItemLabelGenerator { "${it.name} - ${it.label}" }
    configureLogoUpload()

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
    form.add(paymentTerm, fiscalPosition)
    form.add(incoterm, incotermLocation)
    form.add(deliveryPort, 2)
    if (mode == ClientFormMode.OWN_COMPANY) {
      form.add(logoUpload, clearLogoButton)
      form.add(logoPreview, 2)
    }
    form.add(billingAddress, 2)
    form.add(notes, 2)

    contactGrid.addColumn(Contact::lastName).setHeader("Nom").setFlexGrow(1)
    contactGrid.addColumn(Contact::firstName).setHeader("Prénom").setAutoWidth(true)
    contactGrid.addColumn(Contact::email).setHeader("Email").setAutoWidth(true)
    contactGrid.addColumn(Contact::phone).setHeader("Téléphone").setAutoWidth(true)
    contactGrid.addColumn { it.role.name }.setHeader("Rôle").setAutoWidth(true)
    contactGrid.setHeight("200px")

    deliveryAddressGrid.addColumn(ClientDeliveryAddress::label).setHeader("Libellé").setFlexGrow(1)
    deliveryAddressGrid
      .addColumn(ClientDeliveryAddress::address)
      .setHeader("Adresse")
      .setFlexGrow(2)
    deliveryAddressGrid
      .addColumn { if (it.defaultAddress) "Oui" else "" }
      .setHeader("Par défaut")
      .setAutoWidth(true)
    deliveryAddressGrid.addItemDoubleClickListener { editDeliveryAddress(it.item) }
    deliveryAddressGrid.setHeight("180px")

    val addContactBtn = Button("Ajouter contact") { addContact() }
    val removeContactBtn = Button("Supprimer") { removeSelectedContact() }
    removeContactBtn.addThemeVariants(ButtonVariant.LUMO_ERROR)
    val addDeliveryAddressBtn = Button("Ajouter adresse") { addDeliveryAddress() }
    val editDeliveryAddressBtn = Button("Modifier adresse") { editSelectedDeliveryAddress() }
    val removeDeliveryAddressBtn = Button("Supprimer adresse") { removeSelectedDeliveryAddress() }
    removeDeliveryAddressBtn.addThemeVariants(ButtonVariant.LUMO_ERROR)

    val contactActions = HorizontalLayout(addContactBtn, removeContactBtn)
    val contactSection = VerticalLayout(H3("Contacts"), contactActions, contactGrid)
    contactSection.isPadding = false
    contactSection.isSpacing = false
    val deliveryAddressActions =
      HorizontalLayout(addDeliveryAddressBtn, editDeliveryAddressBtn, removeDeliveryAddressBtn)
    val deliveryAddressSection =
      VerticalLayout(H3("Adresses de livraison"), deliveryAddressActions, deliveryAddressGrid)
    deliveryAddressSection.isPadding = false
    deliveryAddressSection.isSpacing = false

    val content = VerticalLayout(form, deliveryAddressSection, contactSection)
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
      visibleCompany.value = User.Company.BOTH
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
    paymentTerm.value = c.paymentTerm
    fiscalPosition.value = c.fiscalPosition
    incoterm.value = c.incoterm
    incotermLocation.value = c.incotermLocation ?: ""
    deliveryPort.value = c.deliveryPort ?: ""
    logoDataValue = c.logoData
    updateLogoPreview()
    updateStatusButton(c.status)
    notes.value = c.notes ?: ""
    contacts.addAll(c.contacts)
    contactGrid.setItems(contacts)
    deliveryAddresses.addAll(c.deliveryAddresses)
    deliveryAddressGrid.setItems(deliveryAddresses)
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

  private fun addDeliveryAddress() {
    DeliveryAddressFormDialog(null) { deliveryAddress ->
        if (deliveryAddress.defaultAddress) {
          deliveryAddresses.forEach { it.defaultAddress = false }
        }
        deliveryAddresses.add(deliveryAddress)
        deliveryAddressGrid.setItems(deliveryAddresses)
      }
      .open()
  }

  private fun editSelectedDeliveryAddress() {
    val selected = deliveryAddressGrid.selectedItems.firstOrNull() ?: return
    editDeliveryAddress(selected)
  }

  private fun editDeliveryAddress(deliveryAddress: ClientDeliveryAddress) {
    DeliveryAddressFormDialog(deliveryAddress) { updated ->
        if (updated.defaultAddress) {
          deliveryAddresses.forEach {
            it.defaultAddress = if (updated.id != null) it.id == updated.id else it === updated
          }
        }
        deliveryAddressGrid.setItems(deliveryAddresses)
      }
      .open()
  }

  private fun removeSelectedDeliveryAddress() {
    deliveryAddressGrid.selectedItems.forEach { deliveryAddresses.remove(it) }
    deliveryAddressGrid.setItems(deliveryAddresses)
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
          visibleCompany.value ?: User.Company.BOTH
        }
      c.email = email.value.takeIf { it.isNotBlank() }
      c.phone = phone.value.takeIf { it.isNotBlank() }
      c.website = website.value.takeIf { it.isNotBlank() }
      c.siret = siret.value.takeIf { it.isNotBlank() }
      c.vatNumber = vatNumber.value.takeIf { it.isNotBlank() }
      c.billingAddress = billingAddress.value.takeIf { it.isNotBlank() }
      c.paymentTerm = paymentTerm.value
      c.fiscalPosition = fiscalPosition.value
      c.incoterm = incoterm.value
      c.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
      c.deliveryPort = deliveryPort.value.takeIf { it.isNotBlank() }
      c.logoData = if (mode == ClientFormMode.OWN_COMPANY) logoDataValue else c.logoData
      c.status =
        if (statusToggle.element.getProperty(DATA_STATUS_PROP) == Client.Status.INACTIVE.name) {
          Client.Status.INACTIVE
        } else {
          Client.Status.ACTIVE
        }
      c.notes = notes.value.takeIf { it.isNotBlank() }

      c.contacts.clear()
      for (contact in contacts) {
        contact.client = c
        c.contacts.add(contact)
      }
      val defaultDeliveryAddress = deliveryAddresses.firstOrNull { it.defaultAddress }
      deliveryAddresses.forEach { it.defaultAddress = it === defaultDeliveryAddress }
      c.deliveryAddresses.clear()
      for (deliveryAddress in deliveryAddresses) {
        deliveryAddress.client = c
        c.deliveryAddresses.add(deliveryAddress)
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
    statusToggle.element.setProperty(DATA_STATUS_PROP, status.name)
    statusToggle.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_CONTRAST)
    if (status == Client.Status.ACTIVE) {
      statusToggle.addThemeVariants(ButtonVariant.LUMO_SUCCESS)
    } else {
      statusToggle.addThemeVariants(ButtonVariant.LUMO_CONTRAST)
    }
  }

  private fun configureLogoUpload() {
    logoUpload.setAcceptedFileTypes("image/png", "image/jpeg")
    logoUpload.setMaxFiles(1)
    logoUpload.isDropAllowed = true
    logoPreview.setAlt("Logo de la société")
    clearLogoButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)
    clearLogoButton.addClickListener {
      logoDataValue = null
      updateLogoPreview()
    }
    logoPreview.maxWidth = "240px"
    logoPreview.maxHeight = "120px"
    updateLogoPreview()
  }

  private fun updateLogoPreview() {
    val logo = logoDataValue
    if (logo.isNullOrBlank()) {
      logoPreview.isVisible = false
      logoPreview.src = ""
      clearLogoButton.isEnabled = false
      return
    }
    logoPreview.src = logo
    logoPreview.isVisible = true
    clearLogoButton.isEnabled = true
  }

  enum class ClientFormMode {
    CLIENT,
    OWN_COMPANY,
  }

  companion object {
    private const val DATA_STATUS_PROP = "data-status"
  }
}
