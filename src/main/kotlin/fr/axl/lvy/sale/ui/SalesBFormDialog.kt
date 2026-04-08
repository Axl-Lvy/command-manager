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
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
import fr.axl.lvy.sale.SalesB
import fr.axl.lvy.sale.SalesBService

internal class SalesBFormDialog(
  private val salesBService: SalesBService,
  salesAService: SalesAService,
  incotermService: IncotermService,
  productService: ProductService,
  private val order: SalesB?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Vente B")
  private val orderACombo = ComboBox<SalesA>("Vente A liée")
  private val orderDate = DatePicker("Date vente")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>

  init {
    setHeaderTitle(if (order == null) "Nouvelle vente B" else "Modifier vente B")
    setWidth("900px")
    setHeight("90%")

    orderACombo.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }

    orderACombo.setItems(salesAService.findAll())
    orderACombo.setItemLabelGenerator { "${it.saleNumber} - ${it.client.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderACombo)
    form.add(orderDate, expectedDeliveryDate)
    form.add(incotermCombo, incotermLocation)
    form.add(notes, 2)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.SALES_B,
        clientSupplier = { orderACombo.value?.client },
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
    }
  }

  private fun populateForm(o: SalesB) {
    orderNumber.value = o.saleNumber
    orderACombo.value = o.salesA
    orderDate.value = o.saleDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    notes.value = o.notes ?: ""

    lineEditor.setLines(salesBService.findLines(o.id!!))
  }

  private fun save() {
    if (orderACombo.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: SalesB("", orderACombo.value)
    if (order != null) {
      o.salesA = orderACombo.value
    }
    o.saleDate = orderDate.value
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.incoterms = incotermCombo.value?.name
    o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
    o.notes = if (notes.value.isBlank()) null else notes.value

    val saved = salesBService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.saleNumber

    Notification.show("Vente B enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
