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

internal class OrderAFormDialog(
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
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val subject = TextField("Objet")
  private val purchasePrice = BigDecimalField("Prix achat HT")
  private val currency = TextField("Devise")
  private val exchangeRate = BigDecimalField("Taux de change")
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

    orderNumber.isRequired = true
    clientCombo.isRequired = true
    orderDate.isRequired = true

    clientCombo.setItems(clientService.findAll())
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(expectedDeliveryDate, clientReference, subject)
    form.add(purchasePrice, currency, exchangeRate)
    form.add(incoterms)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor = DocumentLineEditor(productService, DocumentLine.DocumentType.ORDER_A)

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
      orderDate.value = LocalDate.now()
      currency.value = "EUR"
      exchangeRate.value = BigDecimal.ONE
    }
  }

  private fun populateForm(o: OrderA) {
    orderNumber.value = o.orderNumber
    clientCombo.value = o.client
    orderDate.value = o.orderDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    subject.value = o.subject ?: ""
    purchasePrice.value = o.purchasePriceExclTax
    currency.value = o.currency
    exchangeRate.value = o.exchangeRate
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
    if (orderNumber.isEmpty || clientCombo.isEmpty || orderDate.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: OrderA(orderNumber.value, clientCombo.value, orderDate.value)
    if (order != null) {
      o.orderNumber = orderNumber.value
      o.client = clientCombo.value
      o.orderDate = orderDate.value
    }
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
    o.subject = if (subject.value.isBlank()) null else subject.value
    o.purchasePriceExclTax = purchasePrice.value ?: BigDecimal.ZERO
    o.currency = if (currency.value.isBlank()) "EUR" else currency.value
    o.exchangeRate = exchangeRate.value ?: BigDecimal.ONE
    o.incoterms = if (incoterms.value.isBlank()) null else incoterms.value
    o.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
    o.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
    o.notes = if (notes.value.isBlank()) null else notes.value
    o.conditions = if (conditions.value.isBlank()) null else conditions.value

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
      line.documentId = saved.id!!
      line.position = i
      line.recalculate()
      documentLineRepository.save(line)
    }

    saved.recalculateTotals(newLines)
    orderAService.save(saved)

    Notification.show("Commande A enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
