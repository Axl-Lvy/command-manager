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
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.product.ProductService

@Route("commandes-a")
@PageTitle("Commandes A")
@Menu(order = 5.0, icon = "vaadin:clipboard-text", title = "Commandes A")
internal class CommandAListView(
  private val orderAService: OrderAService,
  private val clientService: ClientService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<OrderA>

  init {
    val addBtn = Button("Nouvelle commande A") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderA::orderNumber).setHeader("N° Commande").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(OrderA::orderDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(OrderA::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(OrderA::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande A")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Commandes A", addBtn))
    add(grid)
  }

  private fun openForm(order: OrderA?) {
    CommandAFormDialog(orderAService, clientService, productService, order, this::refreshGrid)
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(orderAService.findAll())
  }
}
