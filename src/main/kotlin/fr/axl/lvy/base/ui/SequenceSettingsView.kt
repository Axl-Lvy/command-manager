package fr.axl.lvy.base.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.confirmdialog.ConfirmDialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.NumberSequenceService

/**
 * Lets administrators restart non-invoice document sequences (sales, Codig orders, Netstone orders)
 * at a chosen number. Invoice numbering is intentionally excluded — it is year-scoped and must stay
 * monotonic.
 */
@Route("parametres/sequences")
@PageTitle("Séquences")
@Menu(order = 104.0, icon = "vaadin:hash", title = "Paramètres/Séquences")
internal class SequenceSettingsView(private val numberSequenceService: NumberSequenceService) :
  VerticalLayout() {

  private val grid: Grid<SequenceRow>
  private val pendingValues: MutableMap<String, Int?> = mutableMapOf()

  init {
    grid = Grid()
    grid.addColumn(SequenceRow::label).setHeader("Série").setAutoWidth(true).setFlexGrow(0)
    grid
      .addColumn(SequenceRow::nextNumberPreview)
      .setHeader("Prochain numéro")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid
      .addComponentColumn { row ->
        IntegerField().apply {
          min = 1
          step = 1
          isStepButtonsVisible = true
          value = row.nextVal.toInt()
          placeholder = row.nextVal.toString()
          addValueChangeListener { e -> pendingValues[row.entityType] = e.value }
        }
      }
      .setHeader("Repartir à")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid
      .addComponentColumn { row ->
        Button("Appliquer") { confirmReset(row) }
          .apply { addThemeVariants(ButtonVariant.LUMO_PRIMARY) }
      }
      .setHeader("Action")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid.setEmptyStateText("Aucune séquence configurable")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)

    refreshGrid()
    initAsListContainer()

    add(ViewToolbar("Séquences"))
    add(grid)
  }

  private fun confirmReset(row: SequenceRow) {
    val newVal = pendingValues[row.entityType] ?: row.nextVal.toInt()
    if (newVal < 1) {
      Notification.show("Le numéro doit être >= 1", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }
    if (newVal.toLong() == row.nextVal) {
      Notification.show("Aucun changement", 3000, Notification.Position.BOTTOM_END)
      return
    }
    val warning =
      if (newVal.toLong() < row.nextVal)
        "\n⚠ La nouvelle valeur est inférieure à l'actuelle : risque de doublons."
      else ""
    ConfirmDialog(
        "Repartir la série « ${row.label} »",
        "Le prochain numéro sera ${formatPreview(row.entityType, newVal.toLong())}.$warning",
        "Confirmer",
      ) {
        try {
          numberSequenceService.resetSequence(row.entityType, newVal.toLong())
          Notification.show("Séquence mise à jour", 3000, Notification.Position.BOTTOM_END)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
          pendingValues.remove(row.entityType)
          refreshGrid()
        } catch (ex: IllegalArgumentException) {
          Notification.show(ex.message ?: "Erreur", 5000, Notification.Position.BOTTOM_END)
            .addThemeVariants(NotificationVariant.LUMO_ERROR)
        }
      }
      .apply { setCancelable(true) }
      .open()
  }

  private fun formatPreview(entityType: String, value: Long): String {
    val cfg = NumberSequenceService.CONFIGS[entityType] ?: return value.toString()
    return cfg.prefix + value.toString().padStart(cfg.padding, '0')
  }

  private fun refreshGrid() {
    grid.setItems(
      NumberSequenceService.RESETTABLE_TYPES.sorted().map { type ->
        val nextVal = numberSequenceService.currentNextVal(type)
        SequenceRow(
          entityType = type,
          label = LABELS[type] ?: type,
          nextVal = nextVal,
          nextNumberPreview = formatPreview(type, nextVal),
        )
      }
    )
  }

  private data class SequenceRow(
    val entityType: String,
    val label: String,
    val nextVal: Long,
    val nextNumberPreview: String,
  )

  companion object {
    private val LABELS =
      mapOf(
        NumberSequenceService.SALES_CODIG to "Vente Codig",
        NumberSequenceService.SALES_NETSTONE to "Vente Netstone",
        NumberSequenceService.ORDER_CODIG to "Commande Codig",
        NumberSequenceService.ORDER_NETSTONE to "Commande Netstone",
      )
  }
}
