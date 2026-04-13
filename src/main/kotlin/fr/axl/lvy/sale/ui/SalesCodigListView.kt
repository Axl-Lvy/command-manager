package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.ui.DeliveryNoteCodigFormDialog
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.ui.CommandCodigFormDialog
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesStatus

@Route("ventes-codig")
@PageTitle("Ventes Codig")
@Menu(order = 3.0, icon = "vaadin:cart", title = "Vente/Codig")
internal class SalesCodigListView(
  private val salesCodigService: SalesCodigService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val productService: ProductService,
  private val deliveryNoteCodigService: DeliveryNoteCodigService,
  private val orderCodigService: OrderCodigService,
) : VerticalLayout() {

  private val grid: Grid<SalesCodig>

  init {
    val addBtn = Button("Nouvelle vente") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesCodig::saleNumber).setHeader("N° Vente").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(SalesCodig::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesCodig::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesCodig::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid
      .addComponentColumn { sale ->
        val viewButton = Button("Vue") { openForm(sale) }
        viewButton.icon = VaadinIcon.EYE.create()
        viewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deliveryButton = Button("Livraison") { openDeliveryForm(sale) }
        deliveryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY)
        deliveryButton.isEnabled = sale.status == SalesStatus.VALIDATED

        HorizontalLayout(viewButton, deliveryButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente Codig")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes Codig", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesCodig?) {
    val loadedOrder = order?.id?.let { salesCodigService.findDetailedById(it).orElse(null) }
    SalesCodigFormDialog(
        salesCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        loadedOrder,
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

    CommandCodigFormDialog(
        orderCodigService,
        clientService,
        incotermService,
        paymentTermService,
        fiscalPositionService,
        productService,
        loadedOrder,
        this::refreshGrid,
        true,
        this::openLinkedSale,
      )
      .open()
  }

  private fun openLinkedSale(order: OrderCodig) {
    val linkedSale =
      order.id?.let { salesCodigService.findByOrderCodigId(it).orElse(null) } ?: return
    openForm(linkedSale)
  }

  private fun openDeliveryForm(sale: SalesCodig) {
    val loadedSale = sale.id?.let { salesCodigService.findDetailedById(it).orElse(null) }
    val orderCodig = loadedSale?.orderCodig
    if (loadedSale == null || loadedSale.status != SalesStatus.VALIDATED || orderCodig == null) {
      Notification.show(
          "La livraison n'est disponible que pour une vente validee avec commande Codig",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val deliveryNote =
      orderCodig.deliveryNote ?: deliveryNoteCodigService.findByOrderCodigId(orderCodig.id!!)
    DeliveryNoteCodigFormDialog(
        deliveryNoteCodigService,
        orderCodig,
        deliveryNote,
        this::refreshGrid,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(salesCodigService.findAll())
  }
}
