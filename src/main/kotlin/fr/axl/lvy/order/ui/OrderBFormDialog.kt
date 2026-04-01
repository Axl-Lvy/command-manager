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
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBService
import fr.axl.lvy.product.ProductService

internal class OrderBFormDialog(
  private val orderBService: OrderBService,
  orderAService: OrderAService,
  productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
  private val order: OrderB?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Commande B")
  private val orderACombo = ComboBox<OrderA>("Commande A liée")
  private val orderDate = DatePicker("Date commande")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor

  init {
    setHeaderTitle(if (order == null) "Nouvelle commande B" else "Modifier commande B")
    setWidth("900px")
    setHeight("90%")

    orderNumber.isRequired = true
    orderACombo.isRequired = true

    orderACombo.setItems(orderAService.findAll())
    orderACombo.setItemLabelGenerator { "${it.orderNumber} - ${it.client?.name ?: ""}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderACombo)
    form.add(orderDate, expectedDeliveryDate)
    form.add(notes, 2)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.ORDER_B) {
        orderACombo.value?.client
      }

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (order != null) {
      populateForm(order)
    }
  }

  private fun populateForm(o: OrderB) {
    orderNumber.value = o.orderNumber
    orderACombo.value = o.orderA
    orderDate.value = o.orderDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    notes.value = o.notes ?: ""

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.ORDER_B,
        o.id!!,
      )
    lineEditor.setLines(lines)
  }

  private fun save() {
    if (orderNumber.isEmpty || orderACombo.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: OrderB(orderNumber.value, orderACombo.value)
    if (order != null) {
      o.orderNumber = orderNumber.value
      o.orderA = orderACombo.value
    }
    o.orderDate = orderDate.value
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.notes = if (notes.value.isBlank()) null else notes.value

    val saved = orderBService.save(o)

    if (order != null) {
      val oldLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
          DocumentLine.DocumentType.ORDER_B,
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
    orderBService.save(saved)

    Notification.show("Commande B enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
