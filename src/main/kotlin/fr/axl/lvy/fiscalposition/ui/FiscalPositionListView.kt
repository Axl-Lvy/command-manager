package fr.axl.lvy.fiscalposition.ui

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
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService

@Route("parametres/positions-fiscales")
@PageTitle("Positions fiscales")
@Menu(order = 103.0, icon = "vaadin:briefcase", title = "Paramètres/Positions fiscales")
internal class FiscalPositionListView(private val fiscalPositionService: FiscalPositionService) :
  VerticalLayout() {

  private val grid: Grid<FiscalPosition>

  init {
    val addBtn = Button("Nouvelle position") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(FiscalPosition::position).setHeader("Position fiscale").setFlexGrow(1)
    grid
      .addComponentColumn { fiscalPosition ->
        val editButton = Button("Modifier") { openForm(fiscalPosition) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deleteButton =
          Button("Supprimer") {
            ConfirmDialog(
                "Supprimer la position fiscale",
                "Voulez-vous vraiment supprimer « ${fiscalPosition.position} » ?",
                "Supprimer",
              ) {
                fiscalPosition.id?.let(fiscalPositionService::delete)
                Notification.show(
                    "Position fiscale supprimée",
                    3000,
                    Notification.Position.BOTTOM_END,
                  )
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
                refreshGrid()
              }
              .apply { setCancelable(true) }
              .open()
          }
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY)

        HorizontalLayout(editButton, deleteButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid.setEmptyStateText("Aucune position fiscale")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Positions fiscales", addBtn))
    add(grid)
  }

  private fun openForm(fiscalPosition: FiscalPosition?) {
    val loadedFiscalPosition =
      fiscalPosition?.id?.let { fiscalPositionService.findById(it).orElse(null) }
    FiscalPositionFormDialog(fiscalPositionService, loadedFiscalPosition, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(fiscalPositionService.findAll())
  }
}
