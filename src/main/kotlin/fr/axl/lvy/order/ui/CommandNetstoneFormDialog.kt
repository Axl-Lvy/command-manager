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
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.ProductService

internal class CommandNetstoneFormDialog(
  private val orderNetstoneService: OrderNetstoneService,
  orderCodigService: OrderCodigService,
  incotermService: IncotermService,
  productService: ProductService,
  private val order: OrderNetstone?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Commande Netstone")
  private val orderCodigCombo = ComboBox<OrderCodig>("Commande Codig liée")
  private val status = ComboBox<OrderNetstone.OrderNetstoneStatus>("Statut")
  private val orderDate = DatePicker("Date commande")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>

  init {
    setHeaderTitle(
      if (order == null) "Nouvelle commande Netstone" else "Modifier commande Netstone"
    )
    setWidth("900px")
    setHeight("90%")

    orderCodigCombo.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    status.setItems(*OrderNetstone.OrderNetstoneStatus.entries.toTypedArray())

    orderCodigCombo.setItems(orderCodigService.findAll())
    orderCodigCombo.setItemLabelGenerator { "${it.orderNumber} - ${it.client.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderCodigCombo)
    form.add(status, orderDate)
    form.add(expectedDeliveryDate, incotermCombo)
    form.add(incotermLocation, notes)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.ORDER_NETSTONE,
        clientSupplier = { orderCodigCombo.value?.client },
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
      status.value = OrderNetstone.OrderNetstoneStatus.SENT
    }
  }

  private fun populateForm(o: OrderNetstone) {
    orderNumber.value = o.orderNumber
    orderCodigCombo.value = o.orderCodig
    status.value = o.status
    orderDate.value = o.orderDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    notes.value = o.notes ?: ""

    lineEditor.setLines(orderNetstoneService.findLines(o.id!!))
  }

  private fun save() {
    if (orderCodigCombo.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: OrderNetstone("", orderCodigCombo.value)
    if (order != null) {
      o.orderCodig = orderCodigCombo.value
    }
    o.status = status.value ?: OrderNetstone.OrderNetstoneStatus.SENT
    o.orderDate = orderDate.value
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.incoterms = incotermCombo.value?.name
    o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
    o.notes = notes.value.takeIf { it.isNotBlank() }

    val saved = orderNetstoneService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.orderNumber

    Notification.show("Commande Netstone enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
