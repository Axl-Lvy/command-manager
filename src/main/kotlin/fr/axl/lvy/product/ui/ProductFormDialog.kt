package fr.axl.lvy.product.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal

internal class ProductFormDialog(
  private val productService: ProductService,
  private val clientService: ClientService,
  private val product: Product?,
  private val onSave: Runnable,
) : Dialog() {

  private val name = TextField("Nom")
  private val specifications = TextArea("Spécifications")
  private val type = ComboBox<Product.ProductType>("Type")
  private val mto = Checkbox("Fabrication sur commande (MTO)")
  private val sellingPrice = BigDecimalField("Prix vente HT")
  private val purchasePrice = BigDecimalField("Prix achat HT")
  private val unitOption = ComboBox<String>("Unité")
  private val customUnit = TextField("Autre unité")
  private val hsCode = TextField("Code HS")
  private val madeIn = ComboBox<String>("Origine")
  private val active = Checkbox("Actif")
  private val clientCodeRows = VerticalLayout()
  private val availableClients: List<Client> = clientService.findAll().filter { it.isClient() }
  private val clientCodeEntries = mutableListOf<ClientCodeRow>()

  init {
    setHeaderTitle(if (product == null) "Nouveau produit" else "Modifier produit")
    setWidth("760px")

    type.setItems(*Product.ProductType.entries.toTypedArray())
    type.setItemLabelGenerator { if (it == Product.ProductType.PRODUCT) "Produit" else "Service" }
    type.addValueChangeListener { e -> updateFieldsForType(e.value) }

    unitOption.setItems(UNIT_MT, UNIT_KG, UNIT_OTHER)
    customUnit.placeholder = "Saisir l'unité"
    customUnit.isVisible = false

    madeIn.setItems(ORIGIN_CHINA, ORIGIN_THAILAND)
    unitOption.addValueChangeListener { updateUnitInputs(it.value) }

    name.isRequired = true
    type.isRequired = true
    clientCodeRows.isPadding = false
    clientCodeRows.isSpacing = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(name, type)
    form.add(specifications, 2)
    form.add(unitOption, customUnit)
    form.add(sellingPrice, purchasePrice)
    form.add(hsCode, madeIn)
    form.add(mto)
    form.add(active)

    val addClientCodeButton = Button("Ajouter code client") { addClientCodeRow() }
    val clientCodesSection = VerticalLayout(addClientCodeButton, clientCodeRows)
    clientCodesSection.isPadding = false
    clientCodesSection.isSpacing = true

    add(VerticalLayout(form, clientCodesSection).apply { isPadding = false })

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (product != null) {
      populateForm(product)
    } else {
      type.value = Product.ProductType.PRODUCT
      active.value = true
    }
  }

  private fun populateForm(p: Product) {
    name.value = p.name
    specifications.value = p.specifications ?: ""
    type.value = p.type
    mto.value = p.mto
    sellingPrice.value = p.sellingPriceExclTax
    purchasePrice.value = p.purchasePriceExclTax
    applyUnitValue(p.type, p.unit)
    hsCode.value = p.hsCode ?: ""
    madeIn.value = p.madeIn
    active.value = p.active
    p.clientProductCodes.forEach { addClientCodeRow(it.client, it.code) }
    updateFieldsForType(p.type)
  }

  private fun save() {
    if (name.isEmpty || type.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val duplicateClientCodes =
      collectClientCodes().groupingBy { it.first.id }.eachCount().filterValues { it > 1 }
    if (duplicateClientCodes.isNotEmpty()) {
      Notification.show(
          "Chaque client ne peut avoir qu'un seul code produit",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val p = product ?: Product(name = name.value)
    p.name = name.value
    p.specifications = if (specifications.value.isBlank()) null else specifications.value
    p.type = type.value
    p.mto = type.value == Product.ProductType.PRODUCT && mto.value
    p.sellingPriceExclTax = sellingPrice.value ?: BigDecimal.ZERO
    p.purchasePriceExclTax = purchasePrice.value ?: BigDecimal.ZERO
    p.unit = resolveUnitValue()
    p.hsCode = if (hsCode.value.isBlank()) null else hsCode.value
    p.madeIn = madeIn.value
    p.replaceClientProductCodes(collectClientCodes())
    p.active = active.value

    productService.save(p)
    Notification.show("Produit enregistré", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }

  private fun updateFieldsForType(productType: Product.ProductType?) {
    val isProduct = productType == Product.ProductType.PRODUCT
    mto.isEnabled = isProduct
    unitOption.isVisible = isProduct
    customUnit.isVisible = isProduct && unitOption.value == UNIT_OTHER
  }

  private fun updateUnitInputs(selectedUnit: String?) {
    customUnit.isVisible = type.value == Product.ProductType.PRODUCT && selectedUnit == UNIT_OTHER
    if (selectedUnit != UNIT_OTHER) {
      customUnit.clear()
    }
  }

  private fun applyUnitValue(productType: Product.ProductType, unit: String?) {
    if (productType == Product.ProductType.PRODUCT) {
      when (unit) {
        UNIT_MT,
        UNIT_KG -> unitOption.value = unit
        null -> {
          unitOption.clear()
          customUnit.clear()
        }
        else -> {
          unitOption.value = UNIT_OTHER
          customUnit.value = unit
        }
      }
      updateUnitInputs(unitOption.value)
      return
    }

    unitOption.clear()
    customUnit.clear()
  }

  private fun resolveUnitValue(): String? {
    if (type.value == Product.ProductType.PRODUCT) {
      return when (unitOption.value) {
        UNIT_MT,
        UNIT_KG -> unitOption.value
        UNIT_OTHER -> customUnit.value.takeIf { it.isNotBlank() }
        else -> null
      }
    }
    return null
  }

  companion object {
    private const val UNIT_MT = "Mt"
    private const val UNIT_KG = "kg"
    private const val UNIT_OTHER = "Other"
    private const val ORIGIN_CHINA = "China"
    private const val ORIGIN_THAILAND = "Thailand"
  }

  private fun addClientCodeRow(client: Client? = null, code: String = "") {
    val clientCombo = ComboBox<Client>("Client")
    clientCombo.setItems(availableClients)
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }
    clientCombo.value = client

    val codeField = TextField("Code produit client")
    codeField.value = code

    val removeButton = Button("Supprimer")
    removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY)

    val row = HorizontalLayout(clientCombo, codeField, removeButton)
    row.width = "100%"
    row.defaultVerticalComponentAlignment = FlexComponent.Alignment.END
    row.setFlexGrow(1.0, clientCombo, codeField)

    val entry = ClientCodeRow(row, clientCombo, codeField)
    clientCodeEntries.add(entry)
    removeButton.addClickListener {
      clientCodeEntries.remove(entry)
      clientCodeRows.remove(row)
    }
    clientCodeRows.add(row)
  }

  private fun collectClientCodes(): List<Pair<Client, String>> =
    clientCodeEntries.mapNotNull { entry ->
      val client = entry.clientCombo.value
      val code = entry.codeField.value.trim()
      if (client != null && code.isNotBlank()) client to code else null
    }

  private class ClientCodeRow(
    val row: HorizontalLayout,
    val clientCombo: ComboBox<Client>,
    val codeField: TextField,
  )
}
