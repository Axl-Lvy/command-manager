package fr.axl.lvy.incoterm.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.confirmdialog.ConfirmDialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.base.ui.initAsListContainer
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService

@Route("parametres/incoterms")
@PageTitle("Incoterms")
@Menu(order = 100.0, icon = "vaadin:globe", title = "Paramètres/Incoterms")
internal class IncotermListView(private val incotermService: IncotermService) : VerticalLayout() {

  private val grid: Grid<Incoterm>

  init {
    val addBtn = Button("Nouvel incoterm") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Incoterm::name).setHeader("Nom").setAutoWidth(true)
    grid.addColumn(Incoterm::label).setHeader("Libellé").setFlexGrow(1)
    grid
      .addComponentColumn { incoterm ->
        val editButton = Button("Modifier") { openForm(incoterm) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deleteButton =
          Button("Supprimer") {
            ConfirmDialog(
                "Supprimer l'incoterm",
                "Voulez-vous vraiment supprimer l'incoterm « ${incoterm.name} » ?",
                "Supprimer",
              ) {
                incoterm.id?.let(incotermService::delete)
                Notification.show("Incoterm supprimé", 3000, Notification.Position.BOTTOM_END)
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
    grid.setEmptyStateText("Aucun incoterm")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    initAsListContainer()

    add(ViewToolbar("Incoterms", addBtn))
    add(grid)
  }

  private fun openForm(incoterm: Incoterm?) {
    val loadedIncoterm = incoterm?.id?.let { incotermService.findById(it).orElse(null) }
    IncotermFormDialog(incotermService, loadedIncoterm, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems { query ->
      incotermService.findAll(VaadinSpringDataHelpers.toSpringPageRequest(query)).stream()
    }
  }
}
