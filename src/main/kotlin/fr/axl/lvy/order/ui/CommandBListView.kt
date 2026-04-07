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
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBService
import fr.axl.lvy.product.ProductService

@Route("commandes-b")
@PageTitle("Commandes B")
@Menu(order = 6.0, icon = "vaadin:cart-o", title = "Commandes B")
internal class CommandBListView(
  private val orderBService: OrderBService,
  private val orderAService: OrderAService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<OrderB>

  init {
    val addBtn = Button("Nouvelle commande B") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(OrderB::orderNumber).setHeader("N° Commande B").setAutoWidth(true)
    grid.addColumn { it.orderA.orderNumber }.setHeader("Commande A liée").setAutoWidth(true)
    grid.addColumn(OrderB::orderDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(OrderB::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(OrderB::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune commande B")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Commandes B", addBtn))
    add(grid)
  }

  private fun openForm(order: OrderB?) {
    CommandBFormDialog(orderBService, orderAService, productService, order, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(orderBService.findAll())
  }
}
