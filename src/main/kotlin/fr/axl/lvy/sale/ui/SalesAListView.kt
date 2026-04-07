package fr.axl.lvy.sale.ui

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
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService

@Route("ventes-a")
@PageTitle("Ventes A")
@Menu(order = 3.0, icon = "vaadin:cart", title = "Ventes A")
internal class SalesAListView(
  private val salesAService: SalesAService,
  private val clientService: ClientService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesA>

  init {
    val addBtn = Button("Nouvelle vente") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesA::saleNumber).setHeader("N° Vente").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(SalesA::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesA::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesA::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente A")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes A", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesA?) {
    SalesAFormDialog(salesAService, clientService, productService, order, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(salesAService.findAll())
  }
}
