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
import fr.axl.lvy.sale.SalesAService
import fr.axl.lvy.sale.SalesB
import fr.axl.lvy.sale.SalesBService

@Route("ventes-b")
@PageTitle("Ventes B")
@Menu(order = 4.0, icon = "vaadin:truck", title = "Vente/B")
internal class SalesBListView(
  private val salesBService: SalesBService,
  private val salesAService: SalesAService,
  private val incotermService: IncotermService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesB>

  init {
    val addBtn = Button("Nouvelle vente B") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesB::saleNumber).setHeader("N° Vente B").setAutoWidth(true)
    grid.addColumn { it.salesA.saleNumber }.setHeader("Vente A liée").setAutoWidth(true)
    grid.addColumn(SalesB::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesB::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesB::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente B")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes B", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesB?) {
    SalesBFormDialog(
        salesBService,
        salesAService,
        incotermService,
        productService,
        order,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(salesBService.findAll())
  }
}
