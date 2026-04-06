package fr.axl.lvy.order.ui

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
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal
import java.time.LocalDate

internal class CommandAFormDialog(
  private val orderAService: OrderAService,
  clientService: ClientService,
  productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
  private val order: OrderA?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Commande")
  private val clientCombo = ComboBox<Client>("Client")
  private val orderDate = DatePicker("Date commande")
  private val status = ComboBox<OrderA.OrderAStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val subject = TextField("Objet")
  private val totalExclTax = BigDecimalField("Prix vente HT")
  private val currency = ComboBox<String>("Devise")
  private val vatRate = BigDecimalField("TVA (%)")
  private val incoterms = TextField("Incoterms")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor

  init {
    setHeaderTitle(if (order == null) "Nouvelle commande A" else "Modifier commande A")
    setWidth("900px")
    setHeight("90%")

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    totalExclTax.isReadOnly = true
    currency.setItems("EUR", "$")
    status.setItems(*OrderA.OrderAStatus.entries.toTypedArray())

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
    form.add(subject, totalExclTax, currency)
    form.add(vatRate, incoterms)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.ORDER_A) { clientCombo.value }

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
      orderNumber.value = orderAService.nextOrderNumber()
      orderDate.value = LocalDate.now()
      status.value = OrderA.OrderAStatus.CONFIRMED
      currency.value = "EUR"
      vatRate.value = BigDecimal("20.00")
      totalExclTax.value = BigDecimal.ZERO
    }
  }

  private fun populateForm(o: OrderA) {
    orderNumber.value = o.orderNumber
    clientCombo.value = o.client
    orderDate.value = o.orderDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    subject.value = o.subject ?: ""
    totalExclTax.value = o.totalExclTax
    currency.value = o.currency
    vatRate.value = o.vatRate
    incoterms.value = o.incoterms ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_A,
        o.id!!,
      )
    lineEditor.setLines(lines)
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

    val o = order ?: OrderA("", clientCombo.value, orderDate.value)
    if (order != null) {
      o.client = clientCombo.value
      o.orderDate = orderDate.value
    }
    o.status = status.value ?: OrderA.OrderAStatus.CONFIRMED
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.clientReference = clientReference.value.takeIf { it.isNotBlank() }
    o.subject = subject.value.takeIf { it.isNotBlank() }
    o.currency = currency.value ?: "EUR"
    o.vatRate = vatRate.value ?: BigDecimal.ZERO
    o.incoterms = incoterms.value.takeIf { it.isNotBlank() }
    o.billingAddress = billingAddress.value.takeIf { it.isNotBlank() }
    o.shippingAddress = shippingAddress.value.takeIf { it.isNotBlank() }
    o.notes = notes.value.takeIf { it.isNotBlank() }
    o.conditions = conditions.value.takeIf { it.isNotBlank() }

    val saved = orderAService.save(o)

    if (order != null) {
      val oldLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
          DocumentLine.DocumentType.ORDER_A,
          saved.id!!,
        )
      documentLineRepository.deleteAll(oldLines)
    }
    val newLines = lineEditor.getLines()
    newLines.forEachIndexed { i, line ->
      line.documentType = DocumentLine.DocumentType.ORDER_A
      line.documentId = saved.id!!
      line.position = i
      line.vatRate = o.vatRate
      line.recalculate()
      documentLineRepository.save(line)
    }

    saved.recalculateTotals(newLines)
    orderAService.save(saved)
    totalExclTax.value = saved.totalExclTax

    Notification.show("Commande A enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
