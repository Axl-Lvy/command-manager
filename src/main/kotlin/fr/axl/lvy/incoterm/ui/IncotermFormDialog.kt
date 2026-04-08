package fr.axl.lvy.incoterm.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService

internal class IncotermFormDialog(
  private val incotermService: IncotermService,
  private val incoterm: Incoterm?,
  private val onSave: Runnable,
) : Dialog() {

  private val name = TextField("Nom")
  private val label = TextField("Libellé")

  init {
    setHeaderTitle(if (incoterm == null) "Nouvel incoterm" else "Modifier incoterm")
    setWidth("520px")

    name.isRequired = true
    name.maxLength = 20
    name.isAutoselect = true
    label.isRequired = true
    label.maxLength = 255

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 1))
    form.add(name, label)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    incoterm?.let { populateForm(it) }
  }

  private fun populateForm(incoterm: Incoterm) {
    name.value = incoterm.name
    label.value = incoterm.label
  }

  private fun save() {
    if (name.isEmpty || label.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val entity = incoterm ?: Incoterm(name = name.value, label = label.value)
    entity.name = name.value
    entity.label = label.value

    try {
      incotermService.save(entity)
    } catch (e: IllegalArgumentException) {
      Notification.show(e.message ?: "Erreur", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }
    Notification.show("Incoterm enregistré", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
