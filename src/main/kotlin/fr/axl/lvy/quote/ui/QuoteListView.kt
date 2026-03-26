package fr.axl.lvy.quote.ui

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
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.quote.Quote
import fr.axl.lvy.quote.QuoteService

@Route("devis")
@PageTitle("Devis")
@Menu(order = 2.0, icon = "vaadin:file-text-o", title = "Devis")
internal class QuoteListView(
  private val quoteService: QuoteService,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val documentLineRepository: DocumentLineRepository,
) : VerticalLayout() {

  private val grid: Grid<Quote>

  init {
    val addBtn = Button("Nouveau devis") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Quote::quoteNumber).setHeader("N° Devis").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(Quote::quoteDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(Quote::validityDate).setHeader("Validité").setAutoWidth(true)
    grid.addColumn(Quote::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(Quote::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucun devis")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Devis", addBtn))
    add(grid)
  }

  private fun openForm(quote: Quote?) {
    QuoteFormDialog(
        quoteService,
        clientService,
        productService,
        documentLineRepository,
        quote,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(quoteService.findAll())
  }
}
