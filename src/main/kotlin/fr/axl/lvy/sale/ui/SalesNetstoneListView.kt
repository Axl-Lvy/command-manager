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
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
import fr.axl.lvy.order.ui.CommandCodigFormDialog
import fr.axl.lvy.order.ui.CommandNetstoneFormDialog
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesNetstone
import fr.axl.lvy.sale.SalesNetstoneService

@Route("ventes-netstone")
@PageTitle("Ventes Netstone")
@Menu(order = 4.0, icon = "vaadin:truck", title = "Vente/Netstone")
internal class SalesNetstoneListView(
  private val salesNetstoneService: SalesNetstoneService,
  private val orderNetstoneService: OrderNetstoneService,
  private val clientService: ClientService,
  private val salesCodigService: SalesCodigService,
  private val incotermService: IncotermService,
  private val orderCodigService: OrderCodigService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesNetstone>

  init {
    val addBtn = Button("Nouvelle vente Netstone") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesNetstone::saleNumber).setHeader("N° Vente Netstone").setAutoWidth(true)
    grid
      .addColumn {
        val linkedOrder = it.salesCodig.orderCodig
        if (linkedOrder == null) {
          ""
        } else {
          "${linkedOrder.orderNumber} - ${it.salesCodig.client.name}"
        }
      }
      .setHeader("Commande CoDIG liée")
      .setAutoWidth(true)
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
    val loadedOrder = order?.id?.let { salesNetstoneService.findDetailedById(it).orElse(order) }
    val hasLinkedOrder = loadedOrder?.orderNetstone != null
    val hasLinkedCodigOrder = loadedOrder?.salesCodig?.orderCodig != null
    SalesNetstoneFormDialog(
        salesNetstoneService,
        clientService,
        salesCodigService,
        incotermService,
        fiscalPositionService,
        productService,
        loadedOrder,
        this::refreshGrid,
        hasLinkedOrder,
        this::openLinkedOrder,
        hasLinkedCodigOrder,
        this::openLinkedCodigOrder,
        { loadedOrder?.salesCodig?.let(this::openCodigSaleFromNetstone) },
      )
      .open()
  }

  private fun openLinkedOrder(order: OrderNetstone) {
    val linkedOrder =
      order.id?.let { orderNetstoneService.findDetailedById(it).orElse(order) } ?: order
    CommandNetstoneFormDialog(
        orderNetstoneService,
        clientService,
        salesNetstoneService,
        orderCodigService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        linkedOrder,
        this::refreshGrid,
        true,
        this::openLinkedSale,
        { linkedOrder.orderCodig.let(this::openLinkedCodigOrder) },
        { openLinkedCodigSale(linkedOrder.orderCodig) },
      )
      .open()
  }

  private fun openLinkedSale(sale: fr.axl.lvy.sale.SalesNetstone) {
    openForm(sale)
  }

  private fun openLinkedCodigOrder(order: fr.axl.lvy.order.OrderCodig) {
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
        loadedOrder,
        this::refreshGrid,
        hasLinkedSale,
        this::openLinkedCodigSale,
        hasLinkedNetstoneSale,
        this::openLinkedCodigToNetstoneSale,
      )
      .open()
  }

  private fun openLinkedCodigSale(order: fr.axl.lvy.order.OrderCodig) {
    val linkedSale = order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    val loadedSale =
      linkedSale.id?.let { salesCodigService.findDetailedById(it).orElse(linkedSale) } ?: linkedSale
    SalesCodigFormDialog(
        salesCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        loadedSale,
        this::refreshGrid,
        this::openCodigOrderFromSale,
        loadedSale.id?.let { salesNetstoneService.findByOrderCodigId(it).isPresent } == true,
      )
      .open()
  }

  private fun openCodigOrderFromSale(sale: fr.axl.lvy.sale.SalesCodig) {
    val linkedOrder =
      sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }?.orderCodig
        ?: sale.orderCodig
        ?: return
    openLinkedCodigOrder(linkedOrder)
  }

  private fun openCodigSaleFromNetstone(sale: fr.axl.lvy.sale.SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(sale) } ?: sale
    SalesCodigFormDialog(
        salesCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        loadedSale,
        this::refreshGrid,
        this::openCodigOrderFromSale,
      )
      .open()
  }

  private fun openLinkedCodigToNetstoneSale(order: fr.axl.lvy.order.OrderCodig) {
    val linkedSale =
      order.id?.let { salesNetstoneService.findByOrderCodigId(it).orElse(null) } ?: return
    openForm(linkedSale)
  }

  private fun refreshGrid() {
    grid.setItems(salesNetstoneService.findAll())
  }
}
