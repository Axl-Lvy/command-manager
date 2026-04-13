package fr.axl.lvy.currency.ui

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
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.base.ui.initAsListContainer
import fr.axl.lvy.currency.Currency
import fr.axl.lvy.currency.CurrencyService

@Route("parametres/devises")
@PageTitle("Devises")
@Menu(order = 101.0, icon = "vaadin:money", title = "Paramètres/Devises")
internal class CurrencyListView(private val currencyService: CurrencyService) : VerticalLayout() {

  private val grid: Grid<Currency>

  init {
    val addBtn = Button("Nouvelle devise") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Currency::code).setHeader("Devise").setAutoWidth(true)
    grid.addColumn(Currency::symbol).setHeader("Symbole").setAutoWidth(true)
    grid.addColumn(Currency::name).setHeader("Nom").setFlexGrow(1)
    grid
      .addComponentColumn { currency ->
        val editButton = Button("Modifier") { openForm(currency) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deleteButton =
          Button("Supprimer") {
            ConfirmDialog(
                "Supprimer la devise",
                "Voulez-vous vraiment supprimer la devise « ${currency.code} » ?",
                "Supprimer",
              ) {
                currency.id?.let(currencyService::delete)
                Notification.show("Devise supprimée", 3000, Notification.Position.BOTTOM_END)
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
    grid.setEmptyStateText("Aucune devise")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    initAsListContainer()

    add(ViewToolbar("Devises", addBtn))
    add(grid)
  }

  private fun openForm(currency: Currency?) {
    val loadedCurrency = currency?.id?.let { currencyService.findById(it).orElse(null) }
    CurrencyFormDialog(currencyService, loadedCurrency, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(currencyService.findAll())
  }
}
