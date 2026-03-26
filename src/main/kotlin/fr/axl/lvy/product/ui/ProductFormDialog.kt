package fr.axl.lvy.product.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal

internal class ProductFormDialog(
  private val productService: ProductService,
  private val product: Product?,
  private val onSave: Runnable,
) : Dialog() {

  private val reference = TextField("Référence")
  private val designation = TextField("Désignation")
  private val description = TextArea("Description")
  private val type = ComboBox<Product.ProductType>("Type")
  private val mto = Checkbox("Fabrication sur commande (MTO)")
  private val sellingPrice = BigDecimalField("Prix vente HT")
  private val purchasePrice = BigDecimalField("Prix achat HT")
  private val vatRate = BigDecimalField("Taux TVA (%)")
  private val unit = TextField("Unité")
  private val hsCode = TextField("Code HS")
  private val madeIn = TextField("Origine")
  private val clientProductCode = TextField("Code produit client")
  private val active = Checkbox("Actif")

  init {
    setHeaderTitle(if (product == null) "Nouveau produit" else "Modifier produit")
    setWidth("600px")

    type.setItems(*Product.ProductType.entries.toTypedArray())
    type.setItemLabelGenerator { if (it == Product.ProductType.PRODUCT) "Produit" else "Service" }
    type.addValueChangeListener { e -> mto.isEnabled = e.value == Product.ProductType.PRODUCT }

    reference.isRequired = true
    designation.isRequired = true
    type.isRequired = true

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(reference, designation)
    form.add(description, 2)
    form.add(type, unit)
    form.add(sellingPrice, purchasePrice)
    form.add(vatRate, hsCode)
    form.add(madeIn, clientProductCode)
    form.add(mto, active)
    add(form)

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
    reference.value = p.reference
    designation.value = p.designation
    description.value = p.description ?: ""
    type.value = p.type
    mto.value = p.mto
    mto.isEnabled = p.type == Product.ProductType.PRODUCT
    sellingPrice.value = p.sellingPriceExclTax
    purchasePrice.value = p.purchasePriceExclTax
    vatRate.value = p.vatRate
    unit.value = p.unit ?: ""
    hsCode.value = p.hsCode ?: ""
    madeIn.value = p.madeIn ?: ""
    clientProductCode.value = p.clientProductCode ?: ""
    active.value = p.active
  }

  private fun save() {
    if (reference.isEmpty || designation.isEmpty || type.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val p = product ?: Product(reference.value, designation.value)
    if (product != null) {
      p.reference = reference.value
      p.designation = designation.value
    }
    p.description = if (description.value.isBlank()) null else description.value
    p.type = type.value
    p.mto = type.value == Product.ProductType.PRODUCT && mto.value
    p.sellingPriceExclTax = sellingPrice.value ?: BigDecimal.ZERO
    p.purchasePriceExclTax = purchasePrice.value ?: BigDecimal.ZERO
    p.vatRate = vatRate.value ?: BigDecimal.ZERO
    p.unit = if (unit.value.isBlank()) null else unit.value
    p.hsCode = if (hsCode.value.isBlank()) null else hsCode.value
    p.madeIn = if (madeIn.value.isBlank()) null else madeIn.value
    p.clientProductCode = if (clientProductCode.value.isBlank()) null else clientProductCode.value
    p.active = active.value

    productService.save(p)
    Notification.show("Produit enregistré", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
