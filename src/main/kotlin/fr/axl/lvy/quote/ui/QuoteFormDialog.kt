package fr.axl.lvy.quote.ui

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
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.quote.Quote
import fr.axl.lvy.quote.QuoteService
import java.math.BigDecimal
import java.time.LocalDate

internal class QuoteFormDialog(
  private val quoteService: QuoteService,
  clientService: ClientService,
  productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
  private val quote: Quote?,
  private val onSave: Runnable,
) : Dialog() {

  private val quoteNumber = TextField("N° Devis")
  private val clientCombo = ComboBox<Client>("Client")
  private val quoteDate = DatePicker("Date devis")
  private val validityDate = DatePicker("Date validité")
  private val clientReference = TextField("Réf. client")
  private val subject = TextField("Objet")
  private val currency = TextField("Devise")
  private val exchangeRate = BigDecimalField("Taux de change")
  private val incoterms = TextField("Incoterms")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor

  init {
    setHeaderTitle(if (quote == null) "Nouveau devis" else "Modifier devis")
    setWidth("900px")
    setHeight("90%")

    quoteNumber.isRequired = true
    clientCombo.isRequired = true
    quoteDate.isRequired = true

    clientCombo.setItems(clientService.findAll())
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(quoteNumber, clientCombo, quoteDate)
    form.add(validityDate, clientReference, subject)
    form.add(currency, exchangeRate, incoterms)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor = DocumentLineEditor(productService, DocumentLine.DocumentType.QUOTE)

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (quote != null) {
      populateForm(quote)
    } else {
      quoteDate.value = LocalDate.now()
      currency.value = "EUR"
      exchangeRate.value = BigDecimal.ONE
    }
  }

  private fun populateForm(q: Quote) {
    quoteNumber.value = q.quoteNumber
    clientCombo.value = q.client
    quoteDate.value = q.quoteDate
    validityDate.value = q.validityDate
    clientReference.value = q.clientReference ?: ""
    subject.value = q.subject ?: ""
    currency.value = q.currency
    exchangeRate.value = q.exchangeRate
    incoterms.value = q.incoterms ?: ""
    billingAddress.value = q.billingAddress ?: ""
    shippingAddress.value = q.shippingAddress ?: ""
    notes.value = q.notes ?: ""
    conditions.value = q.conditions ?: ""

    val lines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        DocumentLine.DocumentType.QUOTE,
        q.id!!,
      )
    lineEditor.setLines(lines)
  }

  private fun save() {
    if (quoteNumber.isEmpty || clientCombo.isEmpty || quoteDate.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val q = quote ?: Quote(quoteNumber.value, clientCombo.value, quoteDate.value)
    if (quote != null) {
      q.quoteNumber = quoteNumber.value
      q.client = clientCombo.value
      q.quoteDate = quoteDate.value
    }
    q.validityDate = validityDate.value
    q.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
    q.subject = if (subject.value.isBlank()) null else subject.value
    q.currency = if (currency.value.isBlank()) "EUR" else currency.value
    q.exchangeRate = exchangeRate.value ?: BigDecimal.ONE
    q.incoterms = if (incoterms.value.isBlank()) null else incoterms.value
    q.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
    q.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
    q.notes = if (notes.value.isBlank()) null else notes.value
    q.conditions = if (conditions.value.isBlank()) null else conditions.value

    val saved = quoteService.save(q)

    if (quote != null) {
      val oldLines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
          DocumentLine.DocumentType.QUOTE,
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
    quoteService.save(saved)

    Notification.show("Devis enregistré", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
