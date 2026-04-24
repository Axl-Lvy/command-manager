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
import com.vaadin.flow.data.provider.DataProvider
import fr.axl.lvy.base.ui.DocumentFlowNavigation
import fr.axl.lvy.base.ui.DocumentFlowNavigator
import fr.axl.lvy.base.ui.DocumentFlowStep
import fr.axl.lvy.base.ui.noGap
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService
import fr.axl.lvy.sale.SalesStatus

internal class SalesNetstoneFormDialog(
  private val salesNetstoneService: SalesNetstoneService,
  private val clientService: ClientService,
  salesCodigService: SalesCodigService,
  incotermService: IncotermService,
  fiscalPositionService: FiscalPositionService,
  productService: ProductService,
  private val order: SalesNetstone?,
  private val onSave: Runnable,
  private val hasLinkedOrder: Boolean = false,
  private val onOpenLinkedOrder: ((OrderNetstone) -> Unit)? = null,
  private val hasLinkedCodigOrder: Boolean = false,
  private val onOpenLinkedCodigOrder: ((OrderCodig) -> Unit)? = null,
  private val onOpenLinkedCodigSale: (() -> Unit)? = null,
) : Dialog() {

  private val orderNumber = TextField("N° Vente Netstone")
  private val orderCodigCombo = ComboBox<SalesCodig>("Commande CoDIG liée")
  private val orderDate = DatePicker("Date vente")
  private val status = ComboBox<SalesStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val fiscalPositionCombo = ComboBox<FiscalPosition>("Position fiscale")
  private val deliveryAddressCombo = ComboBox<ClientDeliveryAddress>("Adresse de livraison")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>
  private var selectedCurrency: String = order?.currency ?: "EUR"

  init {
    setHeaderTitle(if (order == null) "Nouvelle vente Netstone" else "Vente Netstone")
    setWidth("900px")
    setHeight("90%")

    orderCodigCombo.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    status.setItems(*SalesStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        SalesStatus.DRAFT -> "Brouillon"
        SalesStatus.VALIDATED -> "Validee"
        SalesStatus.CANCELLED -> "Annulee"
      }
    }
    fiscalPositionCombo.setItems(fiscalPositionService.findAll())
    fiscalPositionCombo.setItemLabelGenerator { it.position }
    deliveryAddressCombo.setItemLabelGenerator { it.label }

    orderCodigCombo.setItems(
      DataProvider.fromFilteringCallbacks<SalesCodig, String>(
        { query ->
          salesCodigService
            .searchWithLinkedOrder(query.filter.orElse(""), query.offset, query.limit)
            .stream()
        },
        { query -> salesCodigService.countSearchWithLinkedOrder(query.filter.orElse("")) },
      )
    )
    orderCodigCombo.setItemLabelGenerator {
      val linkedOrder = it.orderCodig
      if (linkedOrder == null) {
        it.saleNumber
      } else {
        "${linkedOrder.orderNumber} - ${it.client.name}"
      }
    }
    orderCodigCombo.addValueChangeListener { event ->
      val salesCodig = event.value ?: return@addValueChangeListener
      applyLinkedOrderDefaults(salesCodig)
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderCodigCombo)
    form.add(status, orderDate)
    form.add(expectedDeliveryDate, fiscalPositionCombo)
    form.add(deliveryAddressCombo, incotermCombo)
    form.add(incotermLocation, 2)
    form.add(notes, 2)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.SALES_NETSTONE,
        clientSupplier = { orderCodigCombo.value?.client },
        currencySupplier = { selectedCurrency },
        currencyUpdater = { selectedCurrency = it },
        lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
      )

    val content = VerticalLayout()
    content.noGap()
    buildFlowNavigator()?.let { content.add(it) }
    content.add(form, lineEditor)
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
      status.value = SalesStatus.DRAFT
      fiscalPositionCombo.value =
        clientService.findDefaultCodigSupplier().map { it.fiscalPosition }.orElse(null)
    }
  }

  private fun populateForm(o: SalesNetstone) {
    orderNumber.value = o.saleNumber
    orderCodigCombo.value = o.salesCodig
    orderDate.value = o.saleDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    fiscalPositionCombo.value = o.fiscalPosition
    selectedCurrency = o.currency
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    notes.value = o.notes ?: ""
    applyCodigDeliveryDefaults(o.shippingAddress)

    lineEditor.setLines(salesNetstoneService.findLines(o.id!!))
  }

  private fun applyLinkedOrderDefaults(salesCodig: SalesCodig) {
    val linkedOrder = salesCodig.orderCodig ?: return
    applyCodigDeliveryDefaults(salesCodig.shippingAddress)
    incotermCombo.value = allIncoterms.firstOrNull { it.name == linkedOrder.incoterms }
    incotermLocation.value = linkedOrder.incotermLocation ?: ""
    selectedCurrency = linkedOrder.currency
  }

  private fun applyCodigDeliveryDefaults(selectedAddress: String?) {
    val codig =
      clientService.findDefaultCodigCompany().flatMap { company ->
        company.id?.let { clientService.findDetailedById(it) } ?: java.util.Optional.of(company)
      }
    val deliveryAddresses = codig.map { it.deliveryAddresses.toList() }.orElse(emptyList())
    deliveryAddressCombo.setItems(deliveryAddresses)
    val selected =
      deliveryAddresses.firstOrNull { it.address == selectedAddress }
        ?: deliveryAddresses.firstOrNull { it.defaultAddress }
        ?: deliveryAddresses.firstOrNull()
    deliveryAddressCombo.value = selected
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

    val o = order ?: SalesNetstone("", orderCodigCombo.value)
    if (order != null) {
      o.salesCodig = orderCodigCombo.value
    }
    o.saleDate = orderDate.value
    o.status = status.value ?: SalesStatus.DRAFT
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.fiscalPosition = fiscalPositionCombo.value
    o.currency = selectedCurrency
    o.shippingAddress = deliveryAddressCombo.value?.address
    o.incoterms = incotermCombo.value?.name
    o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
    o.notes = if (notes.value.isBlank()) null else notes.value

    val saved = salesNetstoneService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.saleNumber

    Notification.show("Vente Netstone enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }

  private fun buildFlowNavigator(): DocumentFlowNavigator? {
    val sale = order ?: return null
    val navigation =
      DocumentFlowNavigation(
        currentStep = DocumentFlowStep.SALES_NETSTONE,
        openSalesCodig =
          if (onOpenLinkedCodigSale != null)
            Runnable {
              close()
              onOpenLinkedCodigSale.invoke()
            }
          else null,
        openOrderCodig =
          if (hasLinkedCodigOrder && onOpenLinkedCodigOrder != null)
            Runnable {
              val linkedOrder = sale.salesCodig.orderCodig ?: return@Runnable
              close()
              onOpenLinkedCodigOrder.invoke(linkedOrder)
            }
          else null,
        openOrderNetstone =
          if (hasLinkedOrder && onOpenLinkedOrder != null)
            Runnable {
              val linkedOrder = sale.orderNetstone ?: return@Runnable
              close()
              onOpenLinkedOrder.invoke(linkedOrder)
            }
          else null,
      )
    return if (navigation.hasLinks()) DocumentFlowNavigator(navigation) else null
  }
}
