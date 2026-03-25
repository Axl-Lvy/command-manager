package fr.axl.lvy.product.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import fr.axl.lvy.product.Product;
import fr.axl.lvy.product.ProductService;
import org.jspecify.annotations.Nullable;

final class ProductFormDialog extends Dialog {

  private final ProductService productService;
  private final Runnable onSave;
  private final @Nullable Product product;

  private final TextField reference = new TextField("Référence");
  private final TextField designation = new TextField("Désignation");
  private final TextArea description = new TextArea("Description");
  private final ComboBox<Product.ProductType> type = new ComboBox<>("Type");
  private final Checkbox mto = new Checkbox("Fabrication sur commande (MTO)");
  private final BigDecimalField sellingPrice = new BigDecimalField("Prix vente HT");
  private final BigDecimalField purchasePrice = new BigDecimalField("Prix achat HT");
  private final BigDecimalField vatRate = new BigDecimalField("Taux TVA (%)");
  private final TextField unit = new TextField("Unité");
  private final TextField hsCode = new TextField("Code HS");
  private final TextField madeIn = new TextField("Origine");
  private final TextField clientProductCode = new TextField("Code produit client");
  private final Checkbox active = new Checkbox("Actif");

  ProductFormDialog(
      final ProductService productService, final @Nullable Product product, final Runnable onSave) {
    this.productService = productService;
    this.product = product;
    this.onSave = onSave;

    setHeaderTitle(product == null ? "Nouveau produit" : "Modifier produit");
    setWidth("600px");

    type.setItems(Product.ProductType.values());
    type.setItemLabelGenerator(t -> t == Product.ProductType.PRODUCT ? "Produit" : "Service");
    type.addValueChangeListener(e -> mto.setEnabled(e.getValue() == Product.ProductType.PRODUCT));

    reference.setRequired(true);
    designation.setRequired(true);
    type.setRequired(true);

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.add(reference, designation);
    form.add(description, 2);
    form.add(type, unit);
    form.add(sellingPrice, purchasePrice);
    form.add(vatRate, hsCode);
    form.add(madeIn, clientProductCode);
    form.add(mto, active);

    add(form);

    final var saveBtn = new Button("Enregistrer", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());

    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (product != null) {
      populateForm(product);
    } else {
      type.setValue(Product.ProductType.PRODUCT);
      active.setValue(true);
    }
  }

  private void populateForm(final Product p) {
    reference.setValue(p.getReference());
    designation.setValue(p.getDesignation());
    description.setValue(p.getDescription() != null ? p.getDescription() : "");
    type.setValue(p.getType());
    mto.setValue(p.isMto());
    mto.setEnabled(p.getType() == Product.ProductType.PRODUCT);
    sellingPrice.setValue(p.getSellingPriceExclTax());
    purchasePrice.setValue(p.getPurchasePriceExclTax());
    vatRate.setValue(p.getVatRate());
    unit.setValue(p.getUnit() != null ? p.getUnit() : "");
    hsCode.setValue(p.getHsCode() != null ? p.getHsCode() : "");
    madeIn.setValue(p.getMadeIn() != null ? p.getMadeIn() : "");
    clientProductCode.setValue(p.getClientProductCode() != null ? p.getClientProductCode() : "");
    active.setValue(p.isActive());
  }

  private void save() {
    if (reference.isEmpty() || designation.isEmpty() || type.isEmpty()) {
      Notification.show(
              "Veuillez remplir les champs obligatoires", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final Product p =
        product != null ? product : new Product(reference.getValue(), designation.getValue());
    if (product != null) {
      p.setReference(reference.getValue());
      p.setDesignation(designation.getValue());
    }
    p.setDescription(description.getValue().isBlank() ? null : description.getValue());
    p.setType(type.getValue());
    p.setMto(type.getValue() == Product.ProductType.PRODUCT && mto.getValue());
    p.setSellingPriceExclTax(
        sellingPrice.getValue() != null ? sellingPrice.getValue() : java.math.BigDecimal.ZERO);
    p.setPurchasePriceExclTax(
        purchasePrice.getValue() != null ? purchasePrice.getValue() : java.math.BigDecimal.ZERO);
    p.setVatRate(vatRate.getValue() != null ? vatRate.getValue() : java.math.BigDecimal.ZERO);
    p.setUnit(unit.getValue().isBlank() ? null : unit.getValue());
    p.setHsCode(hsCode.getValue().isBlank() ? null : hsCode.getValue());
    p.setMadeIn(madeIn.getValue().isBlank() ? null : madeIn.getValue());
    p.setClientProductCode(
        clientProductCode.getValue().isBlank() ? null : clientProductCode.getValue());
    p.setActive(active.getValue());

    productService.save(p);
    Notification.show("Produit enregistré", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    onSave.run();
    close();
  }
}
