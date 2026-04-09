package fr.axl.lvy.paymentterm.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService

internal class PaymentTermFormDialog(
  private val paymentTermService: PaymentTermService,
  private val paymentTerm: PaymentTerm?,
  private val onSave: Runnable,
) : Dialog() {

  private val label = TextField("Délai de paiement")

  init {
    setHeaderTitle(
      if (paymentTerm == null) "Nouvelle condition de paiement"
      else "Modifier condition de paiement"
    )
    setWidth("520px")

    label.isRequired = true
    label.maxLength = 100
    label.isAutoselect = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 1))
    form.add(label)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    paymentTerm?.let { populateForm(it) }
  }

  private fun populateForm(paymentTerm: PaymentTerm) {
    label.value = paymentTerm.label
  }

  private fun save() {
    if (label.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val entity = paymentTerm ?: PaymentTerm(label.value)
    entity.label = label.value

    try {
      paymentTermService.save(entity)
    } catch (e: IllegalArgumentException) {
      Notification.show(e.message ?: "Erreur", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    Notification.show("Délai de paiement enregistré", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
