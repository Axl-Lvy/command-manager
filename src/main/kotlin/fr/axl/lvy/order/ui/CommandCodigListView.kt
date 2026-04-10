package fr.axl.lvy.order.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.ui.SalesCodigFormDialog

@Route("commandes-codig")
@PageTitle("Commandes Codig")
@Menu(order = 5.0, icon = "vaadin:clipboard-text", title = "Commande/Codig")
internal class CommandCodigListView(
  private val orderCodigService: OrderCodigService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val productService: ProductService,
  private val salesCodigService: SalesCodigService,
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
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande Codig")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

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

    CommandCodigFormDialog(
        orderCodigService,
        clientService,
        incotermService,
        productService,
        order,
        this::refreshGrid,
        hasLinkedSale,
        this::openLinkedSale,
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
        productService,
        loadedSale,
        this::refreshGrid,
        this::openLinkedOrder,
      )
      .open()
  }

  private fun openLinkedOrder(sale: SalesCodig) {
    val linkedOrder =
      sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }?.orderCodig
        ?: sale.orderCodig
        ?: return
    val loadedOrder =
      linkedOrder.id?.let { orderCodigService.findDetailedById(it).orElse(linkedOrder) }
        ?: linkedOrder
    showOrderDialog(loadedOrder)
  }

  private fun refreshGrid() {
    grid.setItems(orderCodigService.findAll())
  }
}
