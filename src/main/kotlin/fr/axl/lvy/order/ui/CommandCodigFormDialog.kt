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
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal
import java.time.LocalDate

internal class CommandCodigFormDialog(
  private val orderCodigService: OrderCodigService,
  private val clientService: ClientService,
  incotermService: IncotermService,
  productService: ProductService,
  private val order: OrderCodig?,
  private val onSave: Runnable,
  private val hasLinkedSale: Boolean = false,
  private val onOpenLinkedSale: ((OrderCodig) -> Unit)? = null,
) : Dialog() {

  private val orderNumber = TextField("N° Commande")
  private val clientCombo = ComboBox<Client>("Fournisseur")
  private val orderDate = DatePicker("Date commande")
  private val status = ComboBox<OrderCodig.OrderCodigStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val subject = TextField("Objet")
  private val totalExclTax = BigDecimalField("Prix achat HT")
  private val currency = ComboBox<String>("Devise")
  private val vatRate = BigDecimalField("TVA (%)")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val deliveryLocation = TextField("Livrer à")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>

  init {
    setHeaderTitle(if (order == null) "Nouvelle commande CoDIG" else "Commande CoDIG")
    setWidth("900px")
    setHeight("90%")

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    totalExclTax.isReadOnly = true
    currency.setItems("EUR", "USD")
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    status.setItems(*OrderCodig.OrderCodigStatus.entries.toTypedArray())

    clientCombo.setItems(clientService.findAll().filter { it.isSupplierForProduct() })
    clientCombo.setItemLabelGenerator { it.name }
    clientCombo.addValueChangeListener { event ->
      val client = event.value ?: return@addValueChangeListener
      applyClientDefaults(client)
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(status, expectedDeliveryDate, clientReference)
    form.add(subject, totalExclTax, currency)
    form.add(vatRate, incotermCombo, incotermLocation)
    form.add(deliveryLocation, 3)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.ORDER_CODIG,
        clientSupplier = { clientCombo.value },
        usePurchasePrice = true,
      )

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val actions = HorizontalLayout()
    if (order != null && hasLinkedSale && onOpenLinkedSale != null) {
      val saleButton =
        Button("Vente") {
          close()
          onOpenLinkedSale.invoke(order)
        }
      saleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)
      actions.add(saleButton)
    }
    actions.add(saveBtn, cancelBtn)
    footer.add(actions)

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
      orderDate.value = LocalDate.now()
      status.value = OrderCodig.OrderCodigStatus.DRAFT
      currency.value = "EUR"
      vatRate.value = BigDecimal("20.00")
      totalExclTax.value = BigDecimal.ZERO
      clientService.findDefaultCodigSupplier().ifPresent { clientCombo.value = it }
    }
  }

  private fun populateForm(o: OrderCodig) {
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
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    deliveryLocation.value = o.deliveryLocation ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    lineEditor.setLines(orderCodigService.findLines(o.id!!))
  }

  private fun applyClientDefaults(client: Client) {
    val detailedClient =
      client.id?.let { clientService.findDetailedById(it).orElse(client) } ?: client
    billingAddress.value = detailedClient.billingAddress ?: ""
    shippingAddress.value = detailedClient.shippingAddress ?: ""
    incotermCombo.value = allIncoterms.firstOrNull { it.id == detailedClient.incoterm?.id }
    incotermLocation.value = detailedClient.incotermLocation ?: ""
    deliveryLocation.value = detailedClient.deliveryPort ?: ""
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

    val o = order ?: OrderCodig("", clientCombo.value, orderDate.value)
    if (order != null) {
      o.client = clientCombo.value
      o.orderDate = orderDate.value
    }
    o.status = status.value ?: OrderCodig.OrderCodigStatus.DRAFT
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.clientReference = clientReference.value.takeIf { it.isNotBlank() }
    o.subject = subject.value.takeIf { it.isNotBlank() }
    o.currency = currency.value ?: "EUR"
    o.vatRate = vatRate.value ?: BigDecimal.ZERO
    o.incoterms = incotermCombo.value?.name
    o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
    o.deliveryLocation = deliveryLocation.value.takeIf { it.isNotBlank() }
    o.billingAddress = billingAddress.value.takeIf { it.isNotBlank() }
    o.shippingAddress = shippingAddress.value.takeIf { it.isNotBlank() }
    o.notes = notes.value.takeIf { it.isNotBlank() }
    o.conditions = conditions.value.takeIf { it.isNotBlank() }

    val saved = orderCodigService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.orderNumber
    totalExclTax.value = saved.totalExclTax

    Notification.show("Commande Codig enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
