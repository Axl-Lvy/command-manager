package fr.axl.lvy.fiscalposition.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService

internal class FiscalPositionFormDialog(
  private val fiscalPositionService: FiscalPositionService,
  private val fiscalPosition: FiscalPosition?,
  private val onSave: Runnable,
) : Dialog() {

  private val position = TextField("Position fiscale")

  init {
    setHeaderTitle(
      if (fiscalPosition == null) "Nouvelle position fiscale" else "Modifier position fiscale"
    )
    setWidth("520px")

    position.isRequired = true
    position.maxLength = 100
    position.isAutoselect = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 1))
    form.add(position)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    fiscalPosition?.let { populateForm(it) }
  }

  private fun populateForm(fiscalPosition: FiscalPosition) {
    position.value = fiscalPosition.position
  }

  private fun save() {
    if (position.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val entity = fiscalPosition ?: FiscalPosition(position.value)
    entity.position = position.value

    try {
      fiscalPositionService.save(entity)
    } catch (e: IllegalArgumentException) {
      Notification.show(e.message ?: "Erreur", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    Notification.show("Position fiscale enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
