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
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.server.StreamResource
import fr.axl.lvy.base.ui.DocumentFlowNavigation
import fr.axl.lvy.base.ui.DocumentFlowNavigator
import fr.axl.lvy.base.ui.DocumentFlowStep
import fr.axl.lvy.base.ui.noGap
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService
import java.math.BigDecimal

internal class CommandNetstoneFormDialog(
  private val orderNetstoneService: OrderNetstoneService,
  private val clientService: ClientService,
  private val salesNetstoneService: SalesNetstoneService,
  orderCodigService: OrderCodigService,
  incotermService: IncotermService,
  paymentTermService: PaymentTermService,
  fiscalPositionService: FiscalPositionService,
  productService: ProductService,
  private val pdfService: PdfService,
  private val order: OrderNetstone?,
  private val onSave: Runnable,
  private val hasLinkedSale: Boolean = false,
  private val onOpenLinkedSale: ((SalesNetstone) -> Unit)? = null,
  private val onOpenLinkedCodigOrder: (() -> Unit)? = null,
  private val onOpenLinkedCodigSale: (() -> Unit)? = null,
) : Dialog() {

  private val orderNumber = TextField("N° Commande Netstone")
  private val orderCodigCombo = ComboBox<OrderCodig>("Commande Codig liée")
  private val supplierCombo = ComboBox<Client>("Fournisseur")
  private val status = ComboBox<OrderNetstone.OrderNetstoneStatus>("Statut")
  private val orderDate = DatePicker("Date commande")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val paymentTermCombo = ComboBox<PaymentTerm>("Conditions de paiement")
  private val fiscalPositionCombo = ComboBox<FiscalPosition>("Position fiscale")
  private val deliveryLocationCombo = ComboBox<ClientDeliveryAddress>("Livrer à")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>
  private var selectedCurrency: String = "EUR"
  private val visibleStatuses =
    listOf(
      OrderNetstone.OrderNetstoneStatus.SENT,
      OrderNetstone.OrderNetstoneStatus.CONFIRMED,
      OrderNetstone.OrderNetstoneStatus.CANCELLED,
    )

  init {
    setHeaderTitle(if (order == null) "Nouvelle commande Netstone" else "Commande Netstone")
    setWidth("900px")
    setHeight("90%")

    orderCodigCombo.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    supplierCombo.setItems(clientService.findAll().filter { it.isSupplierForProduct() })
    supplierCombo.setItemLabelGenerator { it.name }
    paymentTermCombo.setItems(paymentTermService.findAll())
    paymentTermCombo.setItemLabelGenerator { it.label }
    fiscalPositionCombo.setItems(fiscalPositionService.findAll())
    fiscalPositionCombo.setItemLabelGenerator { it.position }
    deliveryLocationCombo.setItemLabelGenerator { it.label }
    status.setItems(visibleStatuses)
    status.setItemLabelGenerator(::statusLabel)

    orderCodigCombo.setItems(orderCodigService.findAll())
    orderCodigCombo.setItemLabelGenerator { "${it.orderNumber} - ${it.client.name}" }
    orderCodigCombo.addValueChangeListener { applyLinkedSaleDefaults() }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, supplierCombo)
    form.add(status, orderDate, expectedDeliveryDate)
    form.add(paymentTermCombo, fiscalPositionCombo, incotermCombo)
    form.add(deliveryLocationCombo, incotermLocation)
    form.add(notes, 3)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.ORDER_NETSTONE,
        clientSupplier = { orderCodigCombo.value?.client },
        currencySupplier = { selectedCurrency },
        currencyUpdater = { selectedCurrency = it },
        lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
        defaultVatRate = BigDecimal.ZERO,
      )

    val content = VerticalLayout()
    content.noGap()
    buildFlowNavigator()?.let { content.add(it) }
    content.add(form, lineEditor)
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    val footerLayout = HorizontalLayout(saveBtn, cancelBtn)
    if (order?.id != null) {
      val pdfResource =
        StreamResource("${order.orderNumber}.pdf") {
            pdfService.generateOrderNetstonePdf(order.id!!).inputStream()
          }
          .apply { cacheTime = 0 }
      val pdfBtn = Button("Télécharger PDF")
      val pdfLink =
        Anchor(pdfResource, "").apply {
          element.setAttribute("download", true)
          add(pdfBtn)
        }
      footerLayout.add(pdfLink)
    }
    footer.add(footerLayout)

    if (order != null) {
      populateForm(order)
    } else {
      status.value = OrderNetstone.OrderNetstoneStatus.SENT
      paymentTermCombo.value =
        clientService.findDefaultCodigSupplier().map { it.paymentTerm }.orElse(null)
      fiscalPositionCombo.value =
        clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
      applyCodigDeliveryDefaults(null)
      applyLinkedSaleDefaults()
    }
  }

  private fun populateForm(o: OrderNetstone) {
    orderNumber.value = o.orderNumber
    orderCodigCombo.value = o.orderCodig
    supplierCombo.value = o.supplier
    status.value = normalizeStatusForUi(o.status)
    orderDate.value = o.orderDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    paymentTermCombo.value = o.paymentTerm
    fiscalPositionCombo.value = o.fiscalPosition
    applyCodigDeliveryDefaults(o.deliveryLocation)
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    notes.value = o.notes ?: ""

    lineEditor.setLines(orderNetstoneService.findLines(o.id!!))
  }

  private fun applyLinkedSaleDefaults() {
    val salesCodigId = orderCodigCombo.value?.id ?: return
    val linkedSale = salesNetstoneService.findByOrderCodigId(salesCodigId).orElse(null) ?: return
    incotermCombo.value = allIncoterms.firstOrNull { it.name == linkedSale.incoterms }
    incotermLocation.value = linkedSale.incotermLocation ?: ""
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
    o.supplier = supplierCombo.value
    o.status = status.value ?: OrderNetstone.OrderNetstoneStatus.SENT
    o.orderDate = orderDate.value
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.paymentTerm = paymentTermCombo.value
    o.fiscalPosition = fiscalPositionCombo.value
    o.deliveryLocation = deliveryLocationCombo.value?.address
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

  /**
   * Collapses RECEIVED into CONFIRMED for display: the form only exposes three user-facing states
   * (draft / confirmed / cancelled).
   */
  private fun normalizeStatusForUi(
    status: OrderNetstone.OrderNetstoneStatus
  ): OrderNetstone.OrderNetstoneStatus =
    when (status) {
      OrderNetstone.OrderNetstoneStatus.SENT -> OrderNetstone.OrderNetstoneStatus.SENT
      OrderNetstone.OrderNetstoneStatus.CANCELLED -> OrderNetstone.OrderNetstoneStatus.CANCELLED
      else -> OrderNetstone.OrderNetstoneStatus.CONFIRMED
    }

  /**
   * Returns the French display label for an order status.
   *
   * [SENT][OrderNetstone.OrderNetstoneStatus.SENT] is shown as "Brouillon" (draft). The database
   * column stores "SENT" rather than "DRAFT" for backward-compatibility with the existing MySQL
   * enum definition — renaming the value would require a schema migration.
   */
  private fun statusLabel(status: OrderNetstone.OrderNetstoneStatus): String =
    when (normalizeStatusForUi(status)) {
      OrderNetstone.OrderNetstoneStatus.SENT -> "Brouillon"
      OrderNetstone.OrderNetstoneStatus.CONFIRMED -> "Confirme"
      OrderNetstone.OrderNetstoneStatus.CANCELLED -> "Annule"
      else -> status.name
    }

  private fun applyCodigDeliveryDefaults(selectedAddress: String?) {
    val codig =
      clientService
        .findDefaultCodigCompany()
        .flatMap { company ->
          company.id?.let(clientService::findDetailedById) ?: java.util.Optional.of(company)
        }
        .orElse(null) ?: return
    val deliveryAddresses = codig.deliveryAddresses.toList()
    deliveryLocationCombo.setItems(deliveryAddresses)
    val selected =
      deliveryAddresses.firstOrNull { it.address == selectedAddress }
        ?: deliveryAddresses.firstOrNull { it.defaultAddress }
        ?: deliveryAddresses.firstOrNull()
    deliveryLocationCombo.value = selected
  }

  private fun buildFlowNavigator(): DocumentFlowNavigator? {
    val currentOrder = order ?: return null
    val navigation =
      DocumentFlowNavigation(
        currentStep = DocumentFlowStep.ORDER_NETSTONE,
        openSalesCodig =
          if (onOpenLinkedCodigSale != null)
            Runnable {
              close()
              onOpenLinkedCodigSale.invoke()
            }
          else null,
        openOrderCodig =
          if (onOpenLinkedCodigOrder != null)
            Runnable {
              close()
              onOpenLinkedCodigOrder.invoke()
            }
          else null,
        openSalesNetstone =
          if (hasLinkedSale && onOpenLinkedSale != null)
            Runnable {
              val linkedSale =
                currentOrder.orderCodig.id?.let {
                  salesNetstoneService.findByOrderCodigId(it).orElse(null)
                } ?: return@Runnable
              close()
              onOpenLinkedSale.invoke(linkedSale)
            }
          else null,
      )
    return if (navigation.hasLinks()) DocumentFlowNavigator(navigation) else null
  }
}
