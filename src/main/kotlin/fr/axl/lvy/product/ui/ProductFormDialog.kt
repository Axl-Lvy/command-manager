package fr.axl.lvy.product.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.combobox.MultiSelectComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.currency.CurrencyService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductPriceCompany
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal
import org.slf4j.LoggerFactory

internal class ProductFormDialog(
  private val productService: ProductService,
  private val clientService: ClientService,
  private val currencyService: CurrencyService,
  private val product: Product?,
  private val onSave: Runnable,
) : Dialog() {

  private val name = TextField("Nom")
  private val label = TextField("Libellé")
  private val shortDescription = TextArea("Description courte")
  private val longDescription = TextArea("Description longue")
  private val specifications = TextArea("Spécifications")
  private val type = ComboBox<Product.ProductType>("Type")
  private val mto = Checkbox("Fabrication sur commande (MTO)")
  private val suppliers = MultiSelectComboBox<Client>("Fournisseurs")
  private val unitOption = ComboBox<String>("Unité")
  private val customUnit = TextField("Autre unité")
  private val hsCode = TextField("Code HS")
  private val casNumber = TextField("CAS")
  private val ecNumber = TextField("EC")
  private val madeIn = ComboBox<String>("Origine")
  private val active = Checkbox("Actif")
  private val clientCodeRows = VerticalLayout()
  private val purchasePriceSection = FormLayout()
  private val codigPurchasePrice = BigDecimalField("Prix achat CoDIG HT")
  private val codigPurchaseCurrency = Select<String>()
  private val netstonePurchasePrice = BigDecimalField("Prix achat Netstone HT")
  private val netstonePurchaseCurrency = Select<String>()
  private val sellingPriceRows = VerticalLayout()
  private val availableClients: List<Client> = clientService.findClients()
  private val availableSuppliers: List<Client> =
    clientService.findByRole(Client.ClientRole.PRODUCER)
  private val clientCodeEntries = mutableListOf<ClientCodeRow>()
  private val sellingPriceEntries = mutableListOf<SellingPriceRow>()
  private val currencyCodes = currencyService.findAll().map { it.code }

  init {
    setHeaderTitle(if (product == null) "Nouveau produit" else "Modifier produit")
    setWidth("920px")

    type.setItems(*Product.ProductType.entries.toTypedArray())
    type.setItemLabelGenerator { if (it == Product.ProductType.PRODUCT) "Produit" else "Service" }
    type.addValueChangeListener { e -> updateFieldsForType(e.value) }
    suppliers.setItems(availableSuppliers)
    suppliers.setItemLabelGenerator { it.name }
    configureCurrencySelect(codigPurchaseCurrency, "Devise achat CoDIG")
    configureCurrencySelect(netstonePurchaseCurrency, "Devise achat Netstone")

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
    form.add(label, 2)
    form.add(shortDescription, 2)
    form.add(longDescription, 2)
    form.add(mto, 2)
    form.add(specifications, 2)
    form.add(unitOption, customUnit)
    form.add(suppliers, 2)
    form.add(hsCode, casNumber)
    form.add(ecNumber, madeIn)
    form.add(active)

    purchasePriceSection.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    purchasePriceSection.add(codigPurchasePrice, codigPurchaseCurrency)
    purchasePriceSection.add(netstonePurchasePrice, netstonePurchaseCurrency)

    val addClientCodeButton = Button("Ajouter code client") { addClientCodeRow() }
    val clientCodesSection = VerticalLayout(addClientCodeButton, clientCodeRows)
    clientCodesSection.isPadding = false
    clientCodesSection.isSpacing = true

    val addSellingPriceButton = Button("Ajouter prix client") { addSellingPriceRow() }
    val sellingPricesSection = VerticalLayout(addSellingPriceButton, sellingPriceRows)
    sellingPricesSection.isPadding = false
    sellingPricesSection.isSpacing = true

    add(
      VerticalLayout(
          form,
          H4("Prix d'achat"),
          purchasePriceSection,
          H4("Prix de vente CoDIG par client"),
          sellingPricesSection,
          H4("Codes produit client"),
          clientCodesSection,
        )
        .apply { isPadding = false }
    )

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (product != null) {
      populateForm(product)
    } else {
      type.value = Product.ProductType.PRODUCT
      active.value = true
      codigPurchaseCurrency.value = "EUR"
      netstonePurchaseCurrency.value = "EUR"
    }
  }

  private fun populateForm(p: Product) {
    name.value = p.name
    label.value = p.label ?: ""
    shortDescription.value = p.shortDescription ?: ""
    longDescription.value = p.longDescription ?: ""
    specifications.value = p.specifications ?: ""
    type.value = p.type
    mto.value = p.mto
    suppliers.select(p.suppliers)
    applyUnitValue(p.type, p.unit)
    hsCode.value = p.hsCode ?: ""
    casNumber.value = p.casNumber ?: ""
    ecNumber.value = p.ecNumber ?: ""
    madeIn.value = p.madeIn
    active.value = p.active
    p.purchasePrices.forEach { price ->
      when (price.company) {
        ProductPriceCompany.CODIG -> {
          codigPurchasePrice.value = price.priceExclTax
          codigPurchaseCurrency.value = price.currency
        }
        ProductPriceCompany.NETSTONE -> {
          netstonePurchasePrice.value = price.priceExclTax
          netstonePurchaseCurrency.value = price.currency
        }
      }
    }
    p.sellingPrices.forEach { addSellingPriceRow(it.client, it.priceExclTax, it.currency) }
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

    val duplicateSellingPrices =
      collectSellingPrices().groupingBy { it.client.id }.eachCount().filterValues { it > 1 }
    if (duplicateSellingPrices.isNotEmpty()) {
      Notification.show(
          "Chaque client ne peut avoir qu'un seul prix de vente",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    try {
      val p = product ?: Product(name = name.value)
      p.name = name.value
      p.label = label.value.takeIf { it.isNotBlank() }
      p.shortDescription = shortDescription.value.takeIf { it.isNotBlank() }
      p.longDescription = longDescription.value.takeIf { it.isNotBlank() }
      p.specifications = if (specifications.value.isBlank()) null else specifications.value
      p.type = type.value
      p.mto = type.value == Product.ProductType.PRODUCT && mto.value
      p.unit = resolveUnitValue()
      p.hsCode = if (hsCode.value.isBlank()) null else hsCode.value
      p.casNumber = casNumber.value.takeIf { it.isNotBlank() }
      p.ecNumber = ecNumber.value.takeIf { it.isNotBlank() }
      p.madeIn = madeIn.value
      p.replaceClientProductCodes(collectClientCodes())
      p.replaceSuppliers(suppliers.selectedItems)
      p.replacePurchasePrices(collectPurchasePrices())
      p.replaceSellingPrices(collectSellingPrices())
      p.active = active.value

      productService.save(p)
      Notification.show("Produit enregistré", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
      onSave.run()
      close()
    } catch (e: Exception) {
      logger.error("Erreur lors de l'enregistrement du produit", e)
      Notification.show(
          "Erreur lors de l'enregistrement du produit",
          5000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
    }
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

  private fun configureCurrencySelect(select: Select<String>, label: String) {
    select.label = label
    select.setItems(currencyCodes)
    select.value = "EUR"
  }

  private fun collectPurchasePrices(): List<Product.PurchasePriceEntry> = buildList {
    codigPurchasePrice.value?.let { price ->
      add(
        Product.PurchasePriceEntry(
          ProductPriceCompany.CODIG,
          price,
          codigPurchaseCurrency.value ?: "EUR",
        )
      )
    }
    netstonePurchasePrice.value?.let { price ->
      add(
        Product.PurchasePriceEntry(
          ProductPriceCompany.NETSTONE,
          price,
          netstonePurchaseCurrency.value ?: "EUR",
        )
      )
    }
  }

  private fun addSellingPriceRow(
    client: Client? = null,
    price: BigDecimal? = null,
    currency: String = "EUR",
  ) {
    val clientCombo = ComboBox<Client>("Client")
    clientCombo.setItems(availableClients)
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }
    clientCombo.value = client

    val priceField = BigDecimalField("Prix vente HT")
    priceField.value = price

    val currencySelect = Select<String>()
    configureCurrencySelect(currencySelect, "Devise vente")
    currencySelect.value = currency

    val removeButton = Button("Supprimer")
    removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY)

    val row = HorizontalLayout(clientCombo, priceField, currencySelect, removeButton)
    row.width = "100%"
    row.defaultVerticalComponentAlignment = FlexComponent.Alignment.END
    row.setFlexGrow(1.0, clientCombo)

    val entry = SellingPriceRow(row, clientCombo, priceField, currencySelect)
    sellingPriceEntries.add(entry)
    removeButton.addClickListener {
      sellingPriceEntries.remove(entry)
      sellingPriceRows.remove(row)
    }
    sellingPriceRows.add(row)
  }

  private fun collectSellingPrices(): List<Product.SellingPriceEntry> =
    sellingPriceEntries.mapNotNull { entry ->
      val client = entry.clientCombo.value
      val price = entry.priceField.value
      if (client != null && price != null) {
        Product.SellingPriceEntry(client, price, entry.currencySelect.value ?: "EUR")
      } else {
        null
      }
    }

  companion object {
    private val logger = LoggerFactory.getLogger(ProductFormDialog::class.java)
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

  private class SellingPriceRow(
    val row: HorizontalLayout,
    val clientCombo: ComboBox<Client>,
    val priceField: BigDecimalField,
    val currencySelect: Select<String>,
  )
}
