package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
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
import fr.axl.lvy.delivery.DeliveryNoteAService
import fr.axl.lvy.delivery.ui.DeliveryNoteAFormDialog
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService

@Route("ventes-a")
@PageTitle("Ventes A")
@Menu(order = 3.0, icon = "vaadin:cart", title = "Vente/A")
internal class SalesAListView(
  private val salesAService: SalesAService,
  private val clientService: ClientService,
  private val incotermService: IncotermService,
  private val paymentTermService: PaymentTermService,
  private val productService: ProductService,
  private val deliveryNoteAService: DeliveryNoteAService,
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
    grid
      .addComponentColumn { sale ->
        val editButton = Button("Modifier") { openForm(sale) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deliveryButton = Button("Livraison") { openDeliveryForm(sale) }
        deliveryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY)
        deliveryButton.isEnabled = sale.status == SalesA.SalesAStatus.VALIDATED

        HorizontalLayout(editButton, deliveryButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
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
    val loadedOrder = order?.id?.let { salesAService.findDetailedById(it).orElse(null) }
    SalesAFormDialog(
        salesAService,
        clientService,
        incotermService,
        paymentTermService,
        productService,
        loadedOrder,
        this::refreshGrid,
      )
      .open()
  }

  private fun openDeliveryForm(sale: SalesA) {
    val loadedSale = sale.id?.let { salesAService.findDetailedById(it).orElse(null) }
    val orderA = loadedSale?.orderA
    if (
      loadedSale == null || loadedSale.status != SalesA.SalesAStatus.VALIDATED || orderA == null
    ) {
      Notification.show(
          "La livraison n'est disponible que pour une vente validee avec commande A",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val deliveryNote = orderA.deliveryNote ?: deliveryNoteAService.findByOrderAId(orderA.id!!)
    DeliveryNoteAFormDialog(deliveryNoteAService, orderA, deliveryNote, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(salesAService.findAll())
  }
}
