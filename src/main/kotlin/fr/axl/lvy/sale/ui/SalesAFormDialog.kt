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
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
import java.math.BigDecimal
import java.time.LocalDate

internal class SalesAFormDialog(
  private val salesAService: SalesAService,
  clientService: ClientService,
  productService: ProductService,
  private val order: SalesA?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Vente")
  private val clientCombo = ComboBox<Client>("Client")
  private val orderDate = DatePicker("Date vente")
  private val status = ComboBox<SalesA.SalesAStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val subject = TextField("Objet")
  private val sellingPrice = BigDecimalField("Prix vente HT")
  private val currency = ComboBox<String>("Devise")
  private val vatRate = BigDecimalField("TVA (%)")
  private val incoterms = TextField("Incoterms")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor

  init {
    headerTitle = if (order == null) "Nouvelle vente A" else "Modifier vente A"
    width = "900px"
    height = "90%"

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    sellingPrice.isReadOnly = true
    currency.setItems("EUR", "$")
    status.setItems(*SalesA.SalesAStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        SalesA.SalesAStatus.DRAFT -> "Brouillon"
        SalesA.SalesAStatus.VALIDATED -> "Validee"
        SalesA.SalesAStatus.CANCELLED -> "Annulee"
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
    form.add(subject, sellingPrice, currency)
    form.add(vatRate, incoterms)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.SALES_A) { clientCombo.value }

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
      status.value = SalesA.SalesAStatus.DRAFT
      currency.value = "EUR"
      vatRate.value = BigDecimal("20.00")
      sellingPrice.value = BigDecimal.ZERO
    }
  }

  private fun populateForm(o: SalesA) {
    orderNumber.value = o.saleNumber
    clientCombo.value = o.client
    orderDate.value = o.saleDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    subject.value = o.subject ?: ""
    sellingPrice.value = o.totalExclTax
    currency.value = o.currency
    vatRate.value = o.vatRate
    incoterms.value = o.incoterms ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    lineEditor.setLines(salesAService.findLines(o.id!!))
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

    val o = order ?: SalesA("", clientCombo.value, orderDate.value)
    if (order != null) {
      o.client = clientCombo.value
      o.saleDate = orderDate.value
    }
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.status = status.value ?: SalesA.SalesAStatus.DRAFT
    o.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
    o.subject = if (subject.value.isBlank()) null else subject.value
    o.currency = currency.value ?: "EUR"
    o.vatRate = vatRate.value ?: BigDecimal.ZERO
    o.incoterms = if (incoterms.value.isBlank()) null else incoterms.value
    o.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
    o.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
    o.notes = if (notes.value.isBlank()) null else notes.value
    o.conditions = if (conditions.value.isBlank()) null else conditions.value

    val saved = salesAService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.saleNumber
    sellingPrice.value = saved.totalExclTax

    Notification.show("Vente A enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
