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
import fr.axl.lvy.product.ProductService

@Route("commandes-codig")
@PageTitle("Commandes Codig")
@Menu(order = 5.0, icon = "vaadin:clipboard-text", title = "Commande/Codig")
internal class CommandCodigListView(
  private val orderCodigService: OrderCodigService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<OrderCodig>

  init {
    val addBtn = Button("Nouvelle commande Codig") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderCodig::orderNumber).setHeader("N° Commande").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
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
    CommandCodigFormDialog(
        orderCodigService,
        clientService,
        incotermService,
        productService,
        order,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(orderCodigService.findAll())
  }
}
