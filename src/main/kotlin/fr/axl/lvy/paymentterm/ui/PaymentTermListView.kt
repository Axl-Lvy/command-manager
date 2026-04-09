package fr.axl.lvy.paymentterm.ui

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
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService

@Route("parametres/delais-paiement")
@PageTitle("Délais de paiement")
@Menu(order = 102.0, icon = "vaadin:clock", title = "Paramètres/Délais de paiement")
internal class PaymentTermListView(private val paymentTermService: PaymentTermService) :
  VerticalLayout() {

  private val grid: Grid<PaymentTerm>

  init {
    val addBtn = Button("Nouveau délai") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(PaymentTerm::label).setHeader("Délai de paiement").setFlexGrow(1)
    grid
      .addComponentColumn { paymentTerm ->
        val editButton = Button("Modifier") { openForm(paymentTerm) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val deleteButton =
          Button("Supprimer") {
            ConfirmDialog(
                "Supprimer le délai de paiement",
                "Voulez-vous vraiment supprimer « ${paymentTerm.label} » ?",
                "Supprimer",
              ) {
                paymentTerm.id?.let(paymentTermService::delete)
                Notification.show(
                    "Délai de paiement supprimé",
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
    grid.setEmptyStateText("Aucun délai de paiement")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Délais de paiement", addBtn))
    add(grid)
  }

  private fun openForm(paymentTerm: PaymentTerm?) {
    val loadedPaymentTerm = paymentTerm?.id?.let { paymentTermService.findById(it).orElse(null) }
    PaymentTermFormDialog(paymentTermService, loadedPaymentTerm, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(paymentTermService.findAll())
  }
}
