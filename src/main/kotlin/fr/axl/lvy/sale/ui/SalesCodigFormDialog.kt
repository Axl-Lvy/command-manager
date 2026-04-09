package fr.axl.lvy.sale.ui

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
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesStatus
import java.math.BigDecimal
import java.time.LocalDate

internal class SalesCodigFormDialog(
  private val salesCodigService: SalesCodigService,
  clientService: ClientService,
  incotermService: IncotermService,
  paymentTermService: PaymentTermService,
  productService: ProductService,
  private val order: SalesCodig?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Vente")
  private val clientCombo = ComboBox<Client>("Client")
  private val orderDate = DatePicker("Date vente")
  private val status = ComboBox<SalesStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val paymentTermCombo = ComboBox<PaymentTerm>("Conditions de paiement")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>
  private var selectedCurrency: String = order?.currency ?: "EUR"

  init {
    headerTitle = if (order == null) "Nouvelle vente Codig" else "Modifier vente Codig"
    width = "900px"
    height = "90%"

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    paymentTermCombo.setItems(paymentTermService.findAll())
    paymentTermCombo.setItemLabelGenerator { it.label }
    status.setItems(*SalesStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        SalesStatus.DRAFT -> "Brouillon"
        SalesStatus.VALIDATED -> "Validee"
        SalesStatus.CANCELLED -> "Annulee"
      }
    }

    clientCombo.setItems(clientService.findAll().filter { it.isClient() })
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }
    clientCombo.addValueChangeListener { event ->
      val client = event.value ?: return@addValueChangeListener
      billingAddress.value = client.billingAddress ?: ""
      shippingAddress.value = client.shippingAddress ?: ""
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(status, expectedDeliveryDate, clientReference)
    form.add(paymentTermCombo, incotermCombo, incotermLocation)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.SALES_CODIG,
        clientSupplier = { clientCombo.value },
        currencySupplier = { selectedCurrency },
        currencyUpdater = { selectedCurrency = it },
        lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
        defaultVatRate = BigDecimal("20.00"),
      )

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
      orderDate.value = LocalDate.now()
      status.value = SalesStatus.DRAFT
      selectedCurrency = "EUR"
    }
  }

  private fun populateForm(o: SalesCodig) {
    orderNumber.value = o.saleNumber
    clientCombo.value = o.client
    orderDate.value = o.saleDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    paymentTermCombo.value = o.paymentTerm
    selectedCurrency = o.currency
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    lineEditor.setLines(salesCodigService.findLines(o.id!!))
  }

  private fun save() {
    if (clientCombo.isEmpty || orderDate.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    try {
      val o = order ?: SalesCodig("", clientCombo.value, orderDate.value)
      if (order != null) {
        o.client = clientCombo.value
        o.saleDate = orderDate.value
      }
      o.expectedDeliveryDate = expectedDeliveryDate.value
      o.status = status.value ?: SalesStatus.DRAFT
      o.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
      o.subject = null
      o.paymentTerm = paymentTermCombo.value
      o.currency = selectedCurrency
      o.exchangeRate = BigDecimal.ONE
      o.incoterms = incotermCombo.value?.name
      o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
      o.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
      o.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
      o.notes = if (notes.value.isBlank()) null else notes.value
      o.conditions = if (conditions.value.isBlank()) null else conditions.value

      val saved = salesCodigService.saveWithLines(o, lineEditor.getLines())
      orderNumber.value = saved.saleNumber

      Notification.show("Vente Codig enregistrée", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
      onSave.run()
      close()
    } catch (e: Exception) {
      Notification.show(
          e.message ?: "Erreur lors de l'enregistrement de la vente Codig",
          5000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
    }
  }
}
