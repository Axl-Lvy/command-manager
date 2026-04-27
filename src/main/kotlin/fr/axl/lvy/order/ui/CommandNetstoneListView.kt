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
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService
import fr.axl.lvy.sale.ui.SalesCodigFormDialog
import fr.axl.lvy.sale.ui.SalesNetstoneFormDialog

@Route("commandes-netstone")
@PageTitle("Commandes Netstone")
@Menu(order = 6.0, icon = "vaadin:cart-o", title = "Commande/Netstone")
internal class CommandNetstoneListView(
  private val orderNetstoneService: OrderNetstoneService,
  private val salesNetstoneService: SalesNetstoneService,
  private val clientService: ClientService,
  private val orderCodigService: OrderCodigService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val productService: ProductService,
  private val salesCodigService: SalesCodigService,
  private val pdfService: PdfService,
) : VerticalLayout() {

  private val grid: Grid<OrderNetstone>

  init {
    val addBtn = Button("Nouvelle commande Netstone") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderNetstone::orderNumber).setHeader("N° Commande Netstone").setAutoWidth(true)
    grid.addColumn { it.supplier?.name ?: "" }.setHeader("Fournisseur").setFlexGrow(1)
    grid.addColumn(OrderNetstone::orderDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(OrderNetstone::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(OrderNetstone::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { statusLabel(it.status) }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande Netstone")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    initAsListContainer()

    add(ViewToolbar("Commandes Netstone", addBtn))
    add(grid)
  }

  private fun openForm(order: OrderNetstone?) {
    val loadedOrder = order?.id?.let { orderNetstoneService.findDetailedById(it).orElse(order) }
    val hasLinkedSale =
      loadedOrder?.orderCodig?.id?.let { salesNetstoneService.findByOrderCodigId(it).isPresent } ==
        true
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
        hasLinkedSale,
        this::openLinkedSale,
        { loadedOrder?.orderCodig?.let(this::openCodigOrder) },
        { loadedOrder?.orderCodig?.let(this::openLinkedCodigSale) },
      )
      .open()
  }

  private fun openLinkedSale(sale: SalesNetstone) {
    val linkedSale = sale.id?.let { salesNetstoneService.findDetailedById(it).orElse(sale) } ?: sale
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        pdfService,
        linkedSale,
        this::refreshGrid,
        linkedSale.orderNetstone != null,
        this::openLinkedOrder,
        linkedSale.salesCodig.orderCodig != null,
        this::openCodigOrder,
        { linkedSale.salesCodig.orderCodig?.let(this::openLinkedCodigSale) },
      )
      .open()
  }

  private fun openLinkedOrder(order: OrderNetstone) {
    openForm(order)
  }

  private fun openCodigOrder(order: fr.axl.lvy.order.OrderCodig) {
    val loadedOrder =
      order.id?.let { orderCodigService.findDetailedById(it).orElse(order) } ?: order
    val hasLinkedSale =
      loadedOrder.id?.let { salesCodigService.findByOrderCodigId(it).isPresent } == true
    val hasLinkedNetstoneSale =
      loadedOrder.id?.let { salesNetstoneService.findByOrderCodigId(it).isPresent } == true
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
        hasLinkedSale,
        this::openLinkedCodigSale,
        hasLinkedNetstoneSale,
        this::openLinkedNetstoneSaleFromCodig,
        this::openLinkedOrderNetstoneFromCodigSale,
      )
      .open()
  }

  private fun openLinkedCodigSale(order: fr.axl.lvy.order.OrderCodig) {
    val linkedSale =
      order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    val loadedSale =
      linkedSale.id?.let { salesCodigService.findDetailedById(it).orElse(linkedSale) } ?: linkedSale
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
        this::openCodigOrderFromSale,
        loadedSale.salesNetstone != null,
        { loadedSale.salesNetstone?.let(this::openLinkedSale) },
        { loadedSale.salesNetstone?.orderNetstone?.let(this::openLinkedOrder) },
      )
      .open()
  }

  private fun openLinkedOrderNetstoneFromCodigSale(saleOrder: fr.axl.lvy.order.OrderCodig) {
    val linkedSale =
      saleOrder.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    val linkedOrder = linkedSale.orderNetstone ?: return
    openLinkedOrder(linkedOrder)
  }

  private fun openLinkedNetstoneSaleFromCodig(order: fr.axl.lvy.order.OrderCodig) {
    val linkedSale =
      order.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    openLinkedSale(linkedSale)
  }

  private fun openCodigOrderFromSale(sale: fr.axl.lvy.sale.SalesCodig) {
    val linkedOrder =
      sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }?.orderCodig
        ?: sale.orderCodig
        ?: return
    openCodigOrder(linkedOrder)
  }

  private fun refreshGrid() {
    grid.setItems { query ->
      orderNetstoneService.findAll(VaadinSpringDataHelpers.toSpringPageRequest(query)).stream()
    }
  }

  /**
   * Returns the French display label for an order status.
   *
   * [SENT][OrderNetstone.OrderNetstoneStatus.SENT] is shown as "Brouillon" (draft). The database
   * column stores "SENT" rather than "DRAFT" for backward-compatibility with the existing MySQL
   * enum definition — renaming the value would require a schema migration.
   */
  private fun statusLabel(status: OrderNetstone.OrderNetstoneStatus): String =
    when (status) {
      OrderNetstone.OrderNetstoneStatus.SENT -> "Brouillon"
      OrderNetstone.OrderNetstoneStatus.CANCELLED -> "Annule"
      else -> "Confirme"
    }
}
