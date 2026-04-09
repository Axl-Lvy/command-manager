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
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService

@Route("ventes-netstone")
@PageTitle("Ventes Netstone")
@Menu(order = 4.0, icon = "vaadin:truck", title = "Vente/Netstone")
internal class SalesNetstoneListView(
  private val salesNetstoneService: SalesNetstoneService,
  private val salesCodigService: SalesCodigService,
  private val incotermService: IncotermService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesNetstone>

  init {
    val addBtn = Button("Nouvelle vente Netstone") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesNetstone::saleNumber).setHeader("N° Vente Netstone").setAutoWidth(true)
    grid.addColumn { it.salesCodig.saleNumber }.setHeader("Vente Codig liée").setAutoWidth(true)
    grid.addColumn(SalesNetstone::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesNetstone::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesNetstone::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente Netstone")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes Netstone", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesNetstone?) {
    SalesNetstoneFormDialog(
        salesNetstoneService,
        salesCodigService,
        incotermService,
        productService,
        order,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(salesNetstoneService.findAll())
  }
}
