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
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
import fr.axl.lvy.sale.SalesB
import fr.axl.lvy.sale.SalesBService

internal class OrderBFormDialog(
  private val salesBService: SalesBService,
  salesAService: SalesAService,
  productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
  private val order: SalesB?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N° Vente B")
  private val orderACombo = ComboBox<SalesA>("Vente A liée")
  private val orderDate = DatePicker("Date vente")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor

  init {
    setHeaderTitle(if (order == null) "Nouvelle vente B" else "Modifier vente B")
    setWidth("900px")
    setHeight("90%")

    orderACombo.isRequired = true
    orderNumber.isReadOnly = true

    orderACombo.setItems(salesAService.findAll())
    orderACombo.setItemLabelGenerator { "${it.saleNumber} - ${it.client.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderACombo)
    form.add(orderDate, expectedDeliveryDate)
    form.add(notes, 2)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.SALES_B) {
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
    } else {
      orderNumber.value = salesBService.nextSaleNumber()
    }
  }

  private fun populateForm(o: SalesB) {
    orderNumber.value = o.saleNumber
    orderACombo.value = o.salesA
    orderDate.value = o.saleDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    notes.value = o.notes ?: ""

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.SALES_B,
        o.id!!,
      )
    lineEditor.setLines(lines)
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
    o.notes = if (notes.value.isBlank()) null else notes.value

    val saved = salesBService.save(o)

    if (order != null) {
      val oldLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
          DocumentLine.DocumentType.SALES_B,
          saved.id!!,
        )
      documentLineRepository.deleteAll(oldLines)
    }
    val newLines = lineEditor.getLines()
    newLines.forEachIndexed { i, line ->
      line.documentType = DocumentLine.DocumentType.SALES_B
      line.documentId = saved.id!!
      line.position = i
      line.recalculate()
      documentLineRepository.save(line)
    }

    saved.recalculateTotals(newLines)
    salesBService.syncGeneratedOrder(saved, newLines)

    Notification.show("Vente B enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
