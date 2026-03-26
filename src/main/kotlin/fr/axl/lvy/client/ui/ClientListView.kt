package fr.axl.lvy.client.ui

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
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService

@Route("clients")
@PageTitle("Clients")
@Menu(order = 1.0, icon = "vaadin:users", title = "Clients")
internal class ClientListView(private val clientService: ClientService) : VerticalLayout() {

  private val grid: Grid<Client>

  init {
    val addBtn = Button("Nouveau client") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Client::clientCode).setHeader("Code").setAutoWidth(true)
    grid.addColumn(Client::name).setHeader("Nom").setFlexGrow(1)
    grid.addColumn { it.type.name }.setHeader("Type").setAutoWidth(true)
    grid.addColumn { it.role.name }.setHeader("Rôle").setAutoWidth(true)
    grid.addColumn(Client::email).setHeader("Email").setAutoWidth(true)
    grid.addColumn(Client::phone).setHeader("Téléphone").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucun client")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Clients", addBtn))
    add(grid)
  }

  private fun openForm(client: Client?) {
    ClientFormDialog(clientService, client, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(clientService.findAll())
  }
}
