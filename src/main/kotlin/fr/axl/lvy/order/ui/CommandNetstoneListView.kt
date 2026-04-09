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
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.product.ProductService

@Route("commandes-netstone")
@PageTitle("Commandes Netstone")
@Menu(order = 6.0, icon = "vaadin:cart-o", title = "Commande/Netstone")
internal class CommandNetstoneListView(
  private val orderNetstoneService: OrderNetstoneService,
  private val orderCodigService: OrderCodigService,
  private val incotermService: IncotermService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<OrderNetstone>

  init {
    val addBtn = Button("Nouvelle commande Netstone") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderNetstone::orderNumber).setHeader("N° Commande Netstone").setAutoWidth(true)
    grid.addColumn { it.orderCodig.orderNumber }.setHeader("Commande Codig liée").setAutoWidth(true)
    grid.addColumn(OrderNetstone::orderDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(OrderNetstone::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(OrderNetstone::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande Netstone")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Commandes Netstone", addBtn))
    add(grid)
  }

  private fun openForm(order: OrderNetstone?) {
    CommandNetstoneFormDialog(
        orderNetstoneService,
        orderCodigService,
        incotermService,
        productService,
        order,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(orderNetstoneService.findAll())
  }
}
