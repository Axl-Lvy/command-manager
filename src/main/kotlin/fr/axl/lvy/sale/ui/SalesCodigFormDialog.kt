package fr.axl.lvy.sale.ui

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
import com.vaadin.flow.server.streams.DownloadHandler
import com.vaadin.flow.server.streams.DownloadResponse
import fr.axl.lvy.base.ui.DocumentFlowNavigation
import fr.axl.lvy.base.ui.DocumentFlowNavigator
import fr.axl.lvy.base.ui.DocumentFlowStep
import fr.axl.lvy.base.ui.loadAndApplyClientDefaults
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
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesStatus
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.slf4j.LoggerFactory

internal class SalesCodigFormDialog(
  private val salesCodigService: SalesCodigService,
  private val clientService: ClientService,
  incotermService: IncotermService,
  paymentTermService: PaymentTermService,
  fiscalPositionService: FiscalPositionService,
  productService: ProductService,
  private val pdfService: PdfService,
  private val order: SalesCodig?,
  private val onSave: Runnable,
  private val onOpenLinkedOrder: ((SalesCodig) -> Unit)? = null,
  private val hasLinkedNetstoneSale: Boolean = false,
  private val onOpenLinkedNetstoneSale: (() -> Unit)? = null,
  private val onOpenLinkedNetstoneOrder: (() -> Unit)? = null,
) : Dialog() {

  private val orderNumber = TextField("N° Vente")
  private val clientCombo = ComboBox<Client>("Client")
  private val orderDate = DatePicker("Date vente")
  private val status = ComboBox<SalesStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison prévue")
  private val clientReference = TextField("Réf. client")
  private val paymentTermCombo = ComboBox<PaymentTerm>("Conditions de paiement")
  private val fiscalPositionCombo = ComboBox<FiscalPosition>("Position fiscale")
  private val deliveryAddressCombo = ComboBox<ClientDeliveryAddress>("Adresse de livraison")
  private val incotermCombo = ComboBox<Incoterm>("Incoterm")
  private val incotermLocation = TextField("Emplacement")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor
  private val allIncoterms: List<Incoterm>
  private var selectedCurrency: String = order?.currency ?: "EUR"

  init {
    headerTitle = if (order == null) "Nouvelle vente CoDIG" else "Vente CoDIG"
    width = "900px"
    height = "90%"

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    allIncoterms = incotermService.findAll()
    incotermCombo.setItems(allIncoterms)
    incotermCombo.setItemLabelGenerator { it.name }
    paymentTermCombo.setItems(paymentTermService.findAll())
    paymentTermCombo.setItemLabelGenerator { it.label }
    fiscalPositionCombo.setItems(fiscalPositionService.findAll())
    fiscalPositionCombo.setItemLabelGenerator { it.position }
    deliveryAddressCombo.setItemLabelGenerator { it.label }
    deliveryAddressCombo.addValueChangeListener { event ->
      shippingAddress.value = event.value?.address ?: ""
    }
    status.setItems(*SalesStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        SalesStatus.DRAFT -> "Brouillon"
        SalesStatus.VALIDATED -> "Validee"
        SalesStatus.CANCELLED -> "Annulee"
      }
    }

    clientCombo.setItems(clientService.findClients())
    clientCombo.setItemLabelGenerator { it.name }
    clientCombo.addValueChangeListener { event ->
      val client = event.value ?: return@addValueChangeListener
      applyClientDefaults(client)
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(status, expectedDeliveryDate, clientReference)
    form.add(paymentTermCombo, fiscalPositionCombo, deliveryAddressCombo)
    form.add(incotermCombo, incotermLocation)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(
        productService = productService,
        documentType = DocumentLine.DocumentType.SALES_CODIG,
        clientSupplier = { clientCombo.value },
        currencySupplier = { selectedCurrency },
        currencyUpdater = { selectedCurrency = it },
        lineTaxMode = DocumentLineEditor.LineTaxMode.VAT,
        // CoDIG sales default to 0% VAT (export / intra-community); users can override per line.
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
      val fileName = "${order.saleNumber.replace("/", "_")}.pdf"
      val pdfHandler =
        DownloadHandler.fromInputStream {
          val bytes = pdfService.generateSalesCodigPdf(order.id!!)
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
      status.value = SalesStatus.DRAFT
      selectedCurrency = "EUR"
    }
  }

  private fun populateForm(o: SalesCodig) {
    orderNumber.value = o.saleNumber
    clientCombo.value = o.client
    orderDate.value = o.saleDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    paymentTermCombo.value = o.paymentTerm
    fiscalPositionCombo.value = o.fiscalPosition
    deliveryAddressCombo.clear()
    selectedCurrency = o.currency
    incotermCombo.value = allIncoterms.firstOrNull { it.name == o.incoterms }
    incotermLocation.value = o.incotermLocation ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    lineEditor.setLines(salesCodigService.findLines(o.id!!))
  }

  private fun applyClientDefaults(client: Client) {
    val detailed =
      loadAndApplyClientDefaults(
        client,
        clientService,
        billingAddress,
        shippingAddress,
        deliveryAddressCombo,
        incotermCombo,
        incotermLocation,
        allIncoterms,
      )
    paymentTermCombo.value = detailed.paymentTerm
    fiscalPositionCombo.value = detailed.fiscalPosition
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

    try {
      val o = order ?: SalesCodig("", clientCombo.value, orderDate.value)
      if (order != null) {
        o.client = clientCombo.value
        o.saleDate = orderDate.value
      }
      o.expectedDeliveryDate = expectedDeliveryDate.value
      o.status = status.value ?: SalesStatus.DRAFT
      o.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
      o.subject = null
      o.paymentTerm = paymentTermCombo.value
      o.fiscalPosition = fiscalPositionCombo.value
      o.currency = selectedCurrency
      o.exchangeRate = BigDecimal.ONE
      o.incoterms = incotermCombo.value?.name
      o.incotermLocation = incotermLocation.value.takeIf { it.isNotBlank() }
      o.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
      o.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
      o.notes = if (notes.value.isBlank()) null else notes.value
      o.conditions = if (conditions.value.isBlank()) null else conditions.value

      val saved = salesCodigService.saveWithLines(o, lineEditor.getLines())
      orderNumber.value = saved.saleNumber

      Notification.show("Vente Codig enregistrée", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
      onSave.run()
      close()
    } catch (e: Exception) {
      logger.error("Erreur lors de l'enregistrement de la vente CoDIG", e)
      Notification.show(
          "Erreur lors de l'enregistrement de la vente CoDIG",
          5000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(SalesCodigFormDialog::class.java)
  }

  private fun buildFlowNavigator(): DocumentFlowNavigator? {
    val sale = order ?: return null
    if (!hasLinkedNetstoneSale || onOpenLinkedNetstoneSale == null) {
      return null
    }
    val navigation =
      DocumentFlowNavigation(
        currentStep = DocumentFlowStep.SALES_CODIG,
        openOrderCodig =
          if (sale.orderCodig != null && onOpenLinkedOrder != null)
            Runnable {
              close()
              onOpenLinkedOrder.invoke(sale)
            }
          else null,
        openSalesNetstone =
          Runnable {
            close()
            onOpenLinkedNetstoneSale.invoke()
          },
        openOrderNetstone =
          if (onOpenLinkedNetstoneOrder != null)
            Runnable {
              close()
              onOpenLinkedNetstoneOrder.invoke()
            }
          else null,
      )
    return if (navigation.hasLinks()) DocumentFlowNavigator(navigation) else null
  }
}
