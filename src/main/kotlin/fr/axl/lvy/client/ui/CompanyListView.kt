package fr.axl.lvy.client.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.confirmdialog.ConfirmDialog
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
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.paymentterm.PaymentTermService

@Route("societes")
@PageTitle("Sociétés")
@Menu(order = 1.5, icon = "vaadin:building", title = "Sociétés")
internal class CompanyListView(
  private val clientService: ClientService,
  private val paymentTermService: PaymentTermService,
  private val fiscalPositionService: FiscalPositionService,
  private val incotermService: IncotermService,
) : VerticalLayout() {

  private val grid: Grid<Client>

  init {
    val addBtn = Button("Nouvelle société") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Client::name).setHeader("Nom").setFlexGrow(1)
    grid.addColumn(Client::email).setHeader("Email").setAutoWidth(true)
    grid.addColumn(Client::phone).setHeader("Téléphone").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid
      .addComponentColumn { company ->
        val editButton = Button("Modifier") { openForm(company) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val archiveButton =
          Button("Archiver") {
            ConfirmDialog(
                "Archiver la société",
                "Voulez-vous vraiment archiver la société « ${company.name} » ?",
                "Archiver",
              ) {
                company.status = Client.Status.INACTIVE
                clientService.save(company)
                Notification.show("Société archivée", 3000, Notification.Position.BOTTOM_END)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
                refreshGrid()
              }
              .apply { setCancelable(true) }
              .open()
          }
        archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY)
        archiveButton.isEnabled = company.status != Client.Status.INACTIVE

        HorizontalLayout(editButton, archiveButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid.setEmptyStateText("Aucune société")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Sociétés", addBtn))
    add(grid)
  }

  private fun openForm(company: Client?) {
    val loadedCompany = company?.id?.let { clientService.findDetailedById(it).orElse(null) }
    ClientFormDialog(
        clientService,
        paymentTermService,
        fiscalPositionService,
        incotermService,
        loadedCompany,
        this::refreshGrid,
        ClientFormDialog.ClientFormMode.OWN_COMPANY,
      )
      .open()
  }

  private fun refreshGrid() {
    grid.setItems(clientService.findByType(Client.ClientType.OWN_COMPANY))
  }
}
