package fr.axl.lvy.order.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.server.streams.DownloadHandler
import com.vaadin.flow.server.streams.DownloadResponse
import fr.axl.lvy.base.ui.DocumentFlowNavigation
import fr.axl.lvy.base.ui.DocumentFlowNavigator
import fr.axl.lvy.base.ui.DocumentFlowStep
import fr.axl.lvy.base.ui.loadAndApplyClientDefaults
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate

internal class CommandCodigFormDialog(
  private val orderCodigService: OrderCodigService,
  private val clientService: ClientService,
  incotermService: IncotermService,
  paymentTermService: PaymentTermService,
  fiscalPositionService: FiscalPositionService,
  productService: ProductService,
  private val pdfService: PdfService,
  private val order: OrderCodig?,
  private val onSave: Runnable,
  private val hasLinkedSale: Boolean = false,
  private val onOpenLinkedSale: ((OrderCodig) -> Unit)? = null,
  private val hasLinkedNetstoneSale: Boolean = false,
  private val onOpenLinkedNetstoneSale: ((OrderCodig) -> Unit)? = null,
  private val onOpenLinkedNetstoneOrder: ((OrderCodig) -> Unit)? = null,
) : Dialog() {

  private val orderNumber = TextField("N° Commande")
  private val clientCombo = ComboBox<Client>("Fournisseur")
  private val orderDate = DatePicker("Date commande")
  private val status = ComboBox<OrderCodig.OrderCodigStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val totalExclTax = BigDecimalField("Prix achat HT")
  private val paymentTermCombo = ComboBox<PaymentTerm>("Conditions de paiement")
  private val fiscalPositionCombo = ComboBox<FiscalPosition>("Position fiscale")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val deliveryLocation = TextField("Livrer à")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea()
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>
  private var selectedCurrency: String = order?.currency ?: "EUR"
  private val visibleStatuses =
    listOf(
      OrderCodig.OrderCodigStatus.DRAFT,
      OrderCodig.OrderCodigStatus.CONFIRMED,
      OrderCodig.OrderCodigStatus.CANCELLED,
    )

  init {
    setHeaderTitle(if (order == null) "Nouvelle commande CoDIG" else "Commande CoDIG")
    setWidth("900px")
    setHeight("90%")

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    totalExclTax.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    paymentTermCombo.setItems(paymentTermService.findAll())
    paymentTermCombo.setItemLabelGenerator { it.label }
    fiscalPositionCombo.setItems(fiscalPositionService.findAll())
    fiscalPositionCombo.setItemLabelGenerator { it.position }
    status.setItems(visibleStatuses)
    status.setItemLabelGenerator(::statusLabel)

    clientCombo.setItems(clientService.findSuppliersForProduct())
    clientCombo.setItemLabelGenerator { it.name }
    clientCombo.addValueChangeListener { event ->
      val client = event.value ?: return@addValueChangeListener
      applyClientDefaults(client)
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(status, expectedDeliveryDate, clientReference)
    form.add(paymentTermCombo, fiscalPositionCombo, incotermCombo)
    form.add(incotermLocation, deliveryLocation)
    form.add(billingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.ORDER_CODIG,
        clientSupplier = { clientCombo.value },
        currencySupplier = { selectedCurrency },
        currencyUpdater = { selectedCurrency = it },
        usePurchasePrice = true,
        lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
        defaultVatRate = BigDecimal.ZERO,
      )

    val content = VerticalLayout()
    content.isPadding = false
    content.isSpacing = false
    buildFlowNavigator()?.let { content.add(it) }
    content.add(form, lineEditor)
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val footerLayout = HorizontalLayout(saveBtn, cancelBtn)
    if (order?.id != null) {
      val fileName = "${order.orderNumber.replace("/", "_")}.pdf"
      val pdfHandler =
        DownloadHandler.fromInputStream {
          val bytes = pdfService.generateOrderCodigPdf(order.id!!)
          DownloadResponse(
            ByteArrayInputStream(bytes),
            fileName,
            "application/pdf",
            bytes.size.toLong(),
          )
        }
      val pdfBtn = Button("Télécharger PDF")
      val pdfLink = Anchor(pdfHandler, "").apply { add(pdfBtn) }
      footerLayout.add(pdfLink)
    }
    footer.add(footerLayout)

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
      orderDate.value = LocalDate.now()
      status.value = OrderCodig.OrderCodigStatus.DRAFT
      selectedCurrency = "EUR"
      totalExclTax.value = BigDecimal.ZERO
      clientService.findDefaultCodigSupplier().ifPresent { clientCombo.value = it }
      applyCodigCompanyDefaults()
    }
  }

  private fun populateForm(o: OrderCodig) {
    orderNumber.value = o.orderNumber
    clientCombo.value = o.client
    orderDate.value = o.orderDate
    status.value = normalizeStatusForUi(o.status)
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    totalExclTax.value = o.totalExclTax
    paymentTermCombo.value = o.paymentTerm
    fiscalPositionCombo.value = o.fiscalPosition
    selectedCurrency = o.currency
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
    val detailed =
      loadAndApplyClientDefaults(
        client,
        clientService,
        billingAddress,
        shippingAddress,
        null,
        incotermCombo,
        incotermLocation,
        allIncoterms,
      )
    paymentTermCombo.value = detailed.paymentTerm
    deliveryLocation.value = detailed.deliveryPort ?: ""
    applyCodigCompanyDefaults()
  }

  private fun applyCodigCompanyDefaults() {
    val codig =
      clientService.findDefaultCodigCompany().flatMap { company ->
        company.id?.let { clientService.findDetailedById(it) } ?: java.util.Optional.of(company)
      }
    fiscalPositionCombo.value = codig.map { it.fiscalPosition }.orElse(null)
    incotermCombo.value =
      codig
        .map { ownCompany -> allIncoterms.firstOrNull { it.id == ownCompany.incoterm?.id } }
        .orElse(null)
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
    o.subject = null
    o.currency = selectedCurrency
    o.vatRate = BigDecimal.ZERO
    o.paymentTerm = paymentTermCombo.value
    o.fiscalPosition = fiscalPositionCombo.value
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

  private fun normalizeStatusForUi(
    status: OrderCodig.OrderCodigStatus
  ): OrderCodig.OrderCodigStatus =
    when (status) {
      OrderCodig.OrderCodigStatus.DRAFT -> OrderCodig.OrderCodigStatus.DRAFT
      OrderCodig.OrderCodigStatus.CANCELLED -> OrderCodig.OrderCodigStatus.CANCELLED
      else -> OrderCodig.OrderCodigStatus.CONFIRMED
    }

  private fun statusLabel(status: OrderCodig.OrderCodigStatus): String =
    when (normalizeStatusForUi(status)) {
      OrderCodig.OrderCodigStatus.DRAFT -> "Brouillon"
      OrderCodig.OrderCodigStatus.CONFIRMED -> "Confirme"
      OrderCodig.OrderCodigStatus.CANCELLED -> "Annule"
      else -> status.name
    }

  private fun buildFlowNavigator(): DocumentFlowNavigator? {
    val currentOrder = order ?: return null
    if (!hasLinkedNetstoneSale || onOpenLinkedNetstoneSale == null) {
      return null
    }
    val navigation =
      DocumentFlowNavigation(
        currentStep = DocumentFlowStep.ORDER_CODIG,
        openSalesCodig =
          if (hasLinkedSale && onOpenLinkedSale != null)
            Runnable {
              close()
              onOpenLinkedSale.invoke(currentOrder)
            }
          else null,
        openSalesNetstone = {
          close()
          onOpenLinkedNetstoneSale.invoke(currentOrder)
        },
        openOrderNetstone =
          if (onOpenLinkedNetstoneOrder != null)
            Runnable {
              close()
              onOpenLinkedNetstoneOrder.invoke(currentOrder)
            }
          else null,
      )
    return if (navigation.hasLinks()) DocumentFlowNavigator(navigation) else null
  }
}
