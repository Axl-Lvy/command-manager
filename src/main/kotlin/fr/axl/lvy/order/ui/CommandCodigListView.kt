package fr.axl.lvy.order.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.base.ui.initAsListContainer
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService
import fr.axl.lvy.sale.ui.SalesCodigFormDialog
import fr.axl.lvy.sale.ui.SalesNetstoneFormDialog

@Route("commandes-codig")
@PageTitle("Commandes Codig")
@Menu(order = 5.0, icon = "vaadin:clipboard-text", title = "Commande/Codig")
internal class CommandCodigListView(
  private val orderCodigService: OrderCodigService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val productService: ProductService,
  private val salesCodigService: SalesCodigService,
  private val salesNetstoneService: SalesNetstoneService,
  private val orderNetstoneService: OrderNetstoneService,
  private val pdfService: PdfService,
) : VerticalLayout() {

  private val grid: Grid<OrderCodig>

  init {
    val addBtn = Button("Nouvelle commande Codig") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderCodig::orderNumber).setHeader("N° Commande").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Fournisseur").setFlexGrow(1)
    grid.addColumn(OrderCodig::orderDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(OrderCodig::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(OrderCodig::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { statusLabel(it.status) }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande Codig")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    initAsListContainer()

    add(ViewToolbar("Commandes Codig", addBtn))
    add(grid)
  }

  private fun openForm(order: OrderCodig?) {
    val loadedOrder = order?.id?.let { orderCodigService.findDetailedById(it).orElse(order) }
    showOrderDialog(loadedOrder)
  }

  private fun showOrderDialog(order: OrderCodig?) {
    val hasLinkedSale =
      order?.id?.let { salesCodigService.findByOrderCodigId(it).isPresent } == true
    val hasLinkedNetstoneSale =
      order?.id?.let { salesNetstoneService.findByOrderCodigId(it).isPresent } == true

    CommandCodigFormDialog(
        orderCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        order,
        this::refreshGrid,
        hasLinkedSale,
        this::openLinkedSale,
        hasLinkedNetstoneSale,
        this::openLinkedNetstoneSale,
        this::openLinkedNetstoneOrderFromCodigOrder,
      )
      .open()
  }

  private fun openLinkedSale(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    openSaleForm(linkedSale)
  }

  private fun openSaleForm(sale: SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(sale) } ?: sale
    SalesCodigFormDialog(
        salesCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedSale,
        this::refreshGrid,
        this::openLinkedOrder,
        loadedSale.id?.let { salesNetstoneService.findByOrderCodigId(it).isPresent } == true,
        { loadedSale.orderCodig?.let(this::openLinkedNetstoneSale) },
        { loadedSale.orderCodig?.let(this::openLinkedNetstoneOrderFromCodigOrder) },
      )
      .open()
  }

  private fun openLinkedNetstoneSale(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    openNetstoneSaleForm(linkedSale)
  }

  private fun openNetstoneSaleForm(sale: SalesNetstone) {
    val loadedSale = sale.id?.let { salesNetstoneService.findDetailedById(it).orElse(sale) } ?: sale
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        fiscalPositionService,
        productService,
        pdfService,
        loadedSale,
        this::refreshGrid,
        loadedSale.orderNetstone != null,
        this::openLinkedNetstoneOrder,
        loadedSale.salesCodig.orderCodig != null,
        this::openCodigOrder,
        { openSaleForm(loadedSale.salesCodig) },
      )
      .open()
  }

  private fun openLinkedNetstoneOrder(order: OrderNetstone) {
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
        this::openNetstoneSaleForm,
        { openCodigOrder(loadedOrder.orderCodig) },
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

  private fun openLinkedOrder(sale: SalesCodig) {
    val linkedOrder =
      sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }?.orderCodig
        ?: sale.orderCodig
        ?: return
    openCodigOrder(linkedOrder)
  }

  private fun openCodigOrder(order: OrderCodig) {
    val loadedOrder =
      order.id?.let { orderCodigService.findDetailedById(it).orElse(order) } ?: order
    showOrderDialog(loadedOrder)
  }

  private fun refreshGrid() {
    grid.setItems { query ->
      orderCodigService.findAll(VaadinSpringDataHelpers.toSpringPageRequest(query)).stream()
    }
  }

  private fun statusLabel(status: OrderCodig.OrderCodigStatus): String =
    when (status) {
      OrderCodig.OrderCodigStatus.DRAFT -> "Brouillon"
      OrderCodig.OrderCodigStatus.CANCELLED -> "Annule"
      else -> "Confirme"
    }
}
