package fr.axl.lvy.currency.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.currency.Currency
import fr.axl.lvy.currency.CurrencyService

internal class CurrencyFormDialog(
  private val currencyService: CurrencyService,
  private val currency: Currency?,
  private val onSave: Runnable,
) : Dialog() {

  private val code = TextField("Devise")
  private val symbol = TextField("Symbole")
  private val name = TextField("Nom")

  init {
    setHeaderTitle(if (currency == null) "Nouvelle devise" else "Modifier devise")
    setWidth("520px")

    code.isRequired = true
    code.maxLength = 10
    code.isAutoselect = true
    symbol.isRequired = true
    symbol.maxLength = 10
    name.isRequired = true
    name.maxLength = 100

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 1))
    form.add(code, symbol, name)

    add(VerticalLayout(form).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    currency?.let { populateForm(it) }
  }

  private fun populateForm(currency: Currency) {
    code.value = currency.code
    symbol.value = currency.symbol
    name.value = currency.name
  }

  private fun save() {
    if (code.isEmpty || symbol.isEmpty || name.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val entity = currency ?: Currency(code = code.value, symbol = symbol.value, name = name.value)
    entity.code = code.value
    entity.symbol = symbol.value
    entity.name = name.value

    try {
      currencyService.save(entity)
    } catch (e: IllegalArgumentException) {
      Notification.show(e.message ?: "Erreur", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    Notification.show("Devise enregistrée", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
