package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.base.ui.initAsListContainer
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.DeliveryNoteNetstoneService
import fr.axl.lvy.delivery.ui.DeliveryNoteCodigFormDialog
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.invoice.InvoiceCodigService
import fr.axl.lvy.invoice.ui.InvoiceCodigFormDialog
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.order.ui.CommandCodigFormDialog
import fr.axl.lvy.order.ui.CommandNetstoneFormDialog
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstoneService
import fr.axl.lvy.sale.SalesStatus

@Route("ventes-codig")
@PageTitle("Ventes Codig")
@Menu(order = 3.0, icon = "vaadin:cart", title = "Vente/Codig")
internal class SalesCodigListView(
  private val salesCodigService: SalesCodigService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val productService: ProductService,
  private val deliveryNoteCodigService: DeliveryNoteCodigService,
  private val deliveryNoteNetstoneService: DeliveryNoteNetstoneService,
  private val orderCodigService: OrderCodigService,
  private val invoiceCodigService: InvoiceCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val orderNetstoneService: OrderNetstoneService,
  private val pdfService: PdfService,
) : VerticalLayout() {

  private val grid: Grid<SalesCodig>

  init {
    val addBtn = Button("Nouvelle vente") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesCodig::saleNumber).setHeader("N° Vente").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(SalesCodig::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesCodig::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesCodig::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid
      .addComponentColumn { sale ->
        val viewButton = Button("Vue") { openForm(sale) }
        viewButton.icon = VaadinIcon.EYE.create()
        viewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deliveryButton = Button("Livraison") { openDeliveryForm(sale) }
        deliveryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY)
        deliveryButton.isEnabled = sale.status == SalesStatus.VALIDATED
        val invoiceButton = Button("Facture") { openInvoiceForm(sale) }
        invoiceButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY)
        invoiceButton.isEnabled = sale.orderCodig != null

        val packingListButton = Button("Packing List") { openPackingList(sale) }
        packingListButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY)

        HorizontalLayout(viewButton, deliveryButton, invoiceButton, packingListButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente Codig")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    initAsListContainer()

    add(ViewToolbar("Ventes Codig", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesCodig?) {
    val loadedOrder = order?.id?.let { salesCodigService.findDetailedById(it).orElse(null) }
    SalesCodigFormDialog(
        salesCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedOrder,
        this::refreshGrid,
        this::openLinkedOrder,
        loadedOrder?.salesNetstone != null,
        { loadedOrder?.let(this::openLinkedNetstoneSaleFromCodigSale) },
        { loadedOrder?.orderCodig?.let(this::openLinkedNetstoneOrderFromCodigOrder) },
      )
      .open()
  }

  private fun openLinkedOrder(sale: SalesCodig) {
    val reloadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }
    val linkedOrder = (reloadedSale ?: sale).orderCodig ?: return
    val loadedOrder =
      linkedOrder.id?.let { orderCodigService.findDetailedById(it).orElse(linkedOrder) }
        ?: linkedOrder

    CommandCodigFormDialog(
        orderCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedOrder,
        this::refreshGrid,
        true,
        this::openLinkedSale,
        (reloadedSale ?: sale).salesNetstone != null,
        this::openLinkedNetstoneSaleFromCodigOrder,
        this::openLinkedNetstoneOrderFromCodigOrder,
      )
      .open()
  }

  private fun openLinkedSale(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    openForm(linkedSale)
  }

  private fun openLinkedNetstoneSaleFromCodigOrder(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    val loadedSale =
      linkedSale.id?.let { salesNetstoneService.findDetailedById(it).orElse(linkedSale) }
        ?: linkedSale
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedSale,
        this::refreshGrid,
        loadedSale.orderNetstone != null,
        this::openLinkedNetstoneOrder,
        loadedSale.salesCodig.orderCodig != null,
        this::openLinkedOrderCodigFromNetstoneSale,
        { openForm(loadedSale.salesCodig) },
      )
      .open()
  }

  private fun openLinkedNetstoneSaleFromCodigSale(sale: SalesCodig) {
    val salesNetstone = sale.salesNetstone ?: return
    val loadedSale =
      salesNetstone.id?.let { salesNetstoneService.findDetailedById(it).orElse(salesNetstone) }
        ?: salesNetstone
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedSale,
        this::refreshGrid,
        loadedSale.orderNetstone != null,
        this::openLinkedNetstoneOrder,
        loadedSale.salesCodig.orderCodig != null,
        this::openLinkedOrderCodigFromNetstoneSale,
        { openForm(loadedSale.salesCodig) },
      )
      .open()
  }

  private fun openLinkedNetstoneOrder(order: fr.axl.lvy.order.OrderNetstone) {
    val loadedOrder =
      order.id?.let { orderNetstoneService.findDetailedById(it).orElse(order) } ?: order
    CommandNetstoneFormDialog(
        orderNetstoneService,
        clientService,
        salesNetstoneService,
        orderCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedOrder,
        this::refreshGrid,
        true,
        this::openNetstoneSale,
        { loadedOrder.orderCodig.let(this::openLinkedOrderCodigFromNetstoneSale) },
        { openLinkedSale(loadedOrder.orderCodig) },
      )
      .open()
  }

  private fun openLinkedNetstoneOrderFromCodigOrder(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    val linkedOrder = linkedSale.orderNetstone ?: return
    openLinkedNetstoneOrder(linkedOrder)
  }

  private fun openNetstoneSale(sale: fr.axl.lvy.sale.SalesNetstone) {
    val loadedSale = sale.id?.let { salesNetstoneService.findDetailedById(it).orElse(sale) } ?: sale
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedSale,
        this::refreshGrid,
        loadedSale.orderNetstone != null,
        this::openLinkedNetstoneOrder,
        loadedSale.salesCodig.orderCodig != null,
        this::openLinkedOrderCodigFromNetstoneSale,
        { openForm(loadedSale.salesCodig) },
      )
      .open()
  }

  private fun openLinkedOrderCodigFromNetstoneSale(order: OrderCodig) {
    openLinkedOrder(
      order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    )
  }

  private fun openDeliveryForm(sale: SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }
    val orderCodig = loadedSale?.orderCodig
    if (loadedSale == null || loadedSale.status != SalesStatus.VALIDATED || orderCodig == null) {
      Notification.show(
          "La livraison n'est disponible que pour une vente validee avec commande Codig",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val deliveryNote =
      orderCodig.deliveryNote ?: deliveryNoteCodigService.findByOrderCodigId(orderCodig.id!!)
    val netstoneDeliveryNote =
      salesNetstoneService
        .findByOrderCodigId(orderCodig.id!!)
        .orElse(null)
        ?.orderNetstone
        ?.id
        ?.let(deliveryNoteNetstoneService::findByOrderNetstoneId)
    DeliveryNoteCodigFormDialog(
        deliveryNoteCodigService,
        pdfService,
        orderCodig,
        loadedSale.saleNumber,
        loadedSale.clientReference,
        netstoneDeliveryNote,
        deliveryNote,
        this::refreshGrid,
      )
      .open()
  }

  private fun openPackingList(sale: SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(sale) } ?: sale
    val saleLines = loadedSale.id?.let { salesCodigService.findLines(it) } ?: emptyList()
    PackingListDialog(pdfService, loadedSale, saleLines).open()
  }

  private fun openInvoiceForm(sale: SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(sale) } ?: sale
    val orderCodig = loadedSale.orderCodig
    if (orderCodig?.id == null) {
      Notification.show(
          "La facture n'est disponible que pour une vente avec commande CoDIG",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }
    val loadedOrder = orderCodigService.findDetailedById(orderCodig.id!!).orElse(orderCodig)
    val invoice = invoiceCodigService.prepareForSale(loadedSale, loadedOrder)
    val saleLines = salesCodigService.findLines(loadedSale.id!!)
    val netstoneDeliveryNote =
      salesNetstoneService
        .findByOrderCodigId(orderCodig.id!!)
        .orElse(null)
        ?.orderNetstone
        ?.id
        ?.let(deliveryNoteNetstoneService::findByOrderNetstoneId)
    val initialLines = invoice.id?.let { invoiceCodigService.findLines(it) } ?: saleLines

    InvoiceCodigFormDialog(
        invoiceCodigService,
        productService,
        pdfService,
        loadedSale,
        loadedOrder,
        invoice,
        netstoneDeliveryNote,
        saleLines,
        initialLines,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems { query ->
      salesCodigService.findAll(VaadinSpringDataHelpers.toSpringPageRequest(query)).stream()
    }
  }
}
