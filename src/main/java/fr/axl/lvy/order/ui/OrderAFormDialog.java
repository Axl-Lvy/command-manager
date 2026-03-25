package fr.axl.lvy.order.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientService;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.documentline.ui.DocumentLineEditor;
import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderAService;
import fr.axl.lvy.product.ProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

final class OrderAFormDialog extends Dialog {

  private final OrderAService orderAService;
  private final DocumentLineRepository documentLineRepository;
  private final Runnable onSave;
  private final @Nullable OrderA order;

  private final TextField orderNumber = new TextField("N° Commande");
  private final ComboBox<Client> clientCombo = new ComboBox<>("Client");
  private final DatePicker orderDate = new DatePicker("Date commande");
  private final DatePicker expectedDeliveryDate = new DatePicker("Livraison prévue");
  private final TextField clientReference = new TextField("Réf. client");
  private final TextField subject = new TextField("Objet");
  private final BigDecimalField purchasePrice = new BigDecimalField("Prix achat HT");
  private final TextField currency = new TextField("Devise");
  private final BigDecimalField exchangeRate = new BigDecimalField("Taux de change");
  private final TextField incoterms = new TextField("Incoterms");
  private final TextArea billingAddress = new TextArea("Adresse facturation");
  private final TextArea shippingAddress = new TextArea("Adresse livraison");
  private final TextArea notes = new TextArea("Notes");
  private final TextArea conditions = new TextArea("Conditions");
  private final DocumentLineEditor lineEditor;

  OrderAFormDialog(
      final OrderAService orderAService,
      final ClientService clientService,
      final ProductService productService,
      final DocumentLineRepository documentLineRepository,
      final @Nullable OrderA order,
      final Runnable onSave) {
    this.orderAService = orderAService;
    this.documentLineRepository = documentLineRepository;
    this.order = order;
    this.onSave = onSave;

    setHeaderTitle(order == null ? "Nouvelle commande A" : "Modifier commande A");
    setWidth("900px");
    setHeight("90%");

    orderNumber.setRequired(true);
    clientCombo.setRequired(true);
    orderDate.setRequired(true);

    clientCombo.setItems(clientService.findAll());
    clientCombo.setItemLabelGenerator(c -> c.getClientCode() + " - " + c.getName());

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 3));
    form.add(orderNumber, clientCombo, orderDate);
    form.add(expectedDeliveryDate, clientReference, subject);
    form.add(purchasePrice, currency, exchangeRate);
    form.add(incoterms);
    form.add(billingAddress, 3);
    form.add(shippingAddress, 3);
    form.add(notes, 3);
    form.add(conditions, 3);

    lineEditor = new DocumentLineEditor(productService, DocumentLine.DocumentType.ORDER_A);

    final var content = new VerticalLayout(form, lineEditor);
    content.setPadding(false);
    add(content);

    final var saveBtn = new Button("Enregistrer", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());
    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (order != null) {
      populateForm(order);
    } else {
      orderDate.setValue(LocalDate.now());
      currency.setValue("EUR");
      exchangeRate.setValue(BigDecimal.ONE);
    }
  }

  private void populateForm(final OrderA o) {
    orderNumber.setValue(o.getOrderNumber());
    clientCombo.setValue(o.getClient());
    orderDate.setValue(o.getOrderDate());
    expectedDeliveryDate.setValue(o.getExpectedDeliveryDate());
    clientReference.setValue(o.getClientReference() != null ? o.getClientReference() : "");
    subject.setValue(o.getSubject() != null ? o.getSubject() : "");
    purchasePrice.setValue(o.getPurchasePriceExclTax());
    currency.setValue(o.getCurrency());
    exchangeRate.setValue(o.getExchangeRate());
    incoterms.setValue(o.getIncoterms() != null ? o.getIncoterms() : "");
    billingAddress.setValue(o.getBillingAddress() != null ? o.getBillingAddress() : "");
    shippingAddress.setValue(o.getShippingAddress() != null ? o.getShippingAddress() : "");
    notes.setValue(o.getNotes() != null ? o.getNotes() : "");
    conditions.setValue(o.getConditions() != null ? o.getConditions() : "");

    final var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_A, o.getId());
    lineEditor.setLines(lines);
  }

  private void save() {
    if (orderNumber.isEmpty() || clientCombo.isEmpty() || orderDate.isEmpty()) {
      Notification.show(
              "Veuillez remplir les champs obligatoires", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final OrderA o =
        order != null
            ? order
            : new OrderA(orderNumber.getValue(), clientCombo.getValue(), orderDate.getValue());
    if (order != null) {
      o.setOrderNumber(orderNumber.getValue());
      o.setClient(clientCombo.getValue());
      o.setOrderDate(orderDate.getValue());
    }
    o.setExpectedDeliveryDate(expectedDeliveryDate.getValue());
    o.setClientReference(clientReference.getValue().isBlank() ? null : clientReference.getValue());
    o.setSubject(subject.getValue().isBlank() ? null : subject.getValue());
    o.setPurchasePriceExclTax(
        purchasePrice.getValue() != null ? purchasePrice.getValue() : BigDecimal.ZERO);
    o.setCurrency(currency.getValue().isBlank() ? "EUR" : currency.getValue());
    o.setExchangeRate(exchangeRate.getValue() != null ? exchangeRate.getValue() : BigDecimal.ONE);
    o.setIncoterms(incoterms.getValue().isBlank() ? null : incoterms.getValue());
    o.setBillingAddress(billingAddress.getValue().isBlank() ? null : billingAddress.getValue());
    o.setShippingAddress(shippingAddress.getValue().isBlank() ? null : shippingAddress.getValue());
    o.setNotes(notes.getValue().isBlank() ? null : notes.getValue());
    o.setConditions(conditions.getValue().isBlank() ? null : conditions.getValue());

    final OrderA saved = orderAService.save(o);

    if (order != null) {
      final var oldLines =
          documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
              DocumentLine.DocumentType.ORDER_A, saved.getId());
      documentLineRepository.deleteAll(oldLines);
    }
    final var newLines = lineEditor.getLines();
    for (int i = 0; i < newLines.size(); i++) {
      final var line = newLines.get(i);
      line.setDocumentId(saved.getId());
      line.setPosition(i);
      line.recalculate();
      documentLineRepository.save(line);
    }

    saved.recalculateTotals(newLines);
    orderAService.save(saved);

    Notification.show("Commande A enregistrée", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    onSave.run();
    close();
  }
}
