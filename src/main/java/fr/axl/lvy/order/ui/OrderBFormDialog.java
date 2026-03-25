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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.documentline.ui.DocumentLineEditor;
import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderAService;
import fr.axl.lvy.order.OrderB;
import fr.axl.lvy.order.OrderBService;
import fr.axl.lvy.product.ProductService;
import org.jspecify.annotations.Nullable;

final class OrderBFormDialog extends Dialog {

  private final OrderBService orderBService;
  private final OrderAService orderAService;
  private final DocumentLineRepository documentLineRepository;
  private final Runnable onSave;
  private final @Nullable OrderB order;

  private final TextField orderNumber = new TextField("N° Commande B");
  private final ComboBox<OrderA> orderACombo = new ComboBox<>("Commande A liée");
  private final DatePicker orderDate = new DatePicker("Date commande");
  private final DatePicker expectedDeliveryDate = new DatePicker("Livraison prévue");
  private final TextArea notes = new TextArea("Notes");
  private final DocumentLineEditor lineEditor;

  OrderBFormDialog(
      final OrderBService orderBService,
      final OrderAService orderAService,
      final ProductService productService,
      final DocumentLineRepository documentLineRepository,
      final @Nullable OrderB order,
      final Runnable onSave) {
    this.orderBService = orderBService;
    this.orderAService = orderAService;
    this.documentLineRepository = documentLineRepository;
    this.order = order;
    this.onSave = onSave;

    setHeaderTitle(order == null ? "Nouvelle commande B" : "Modifier commande B");
    setWidth("900px");
    setHeight("90%");

    orderNumber.setRequired(true);
    orderACombo.setRequired(true);

    orderACombo.setItems(orderAService.findAll());
    orderACombo.setItemLabelGenerator(
        o -> o.getOrderNumber() + " - " + (o.getClient() != null ? o.getClient().getName() : ""));

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.add(orderNumber, orderACombo);
    form.add(orderDate, expectedDeliveryDate);
    form.add(notes, 2);

    lineEditor = new DocumentLineEditor(productService, DocumentLine.DocumentType.ORDER_B);

    final var content = new VerticalLayout(form, lineEditor);
    content.setPadding(false);
    add(content);

    final var saveBtn = new Button("Enregistrer", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());
    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (order != null) {
      populateForm(order);
    }
  }

  private void populateForm(final OrderB o) {
    orderNumber.setValue(o.getOrderNumber());
    orderACombo.setValue(o.getOrderA());
    orderDate.setValue(o.getOrderDate());
    expectedDeliveryDate.setValue(o.getExpectedDeliveryDate());
    notes.setValue(o.getNotes() != null ? o.getNotes() : "");

    final var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.ORDER_B, o.getId());
    lineEditor.setLines(lines);
  }

  private void save() {
    if (orderNumber.isEmpty() || orderACombo.isEmpty()) {
      Notification.show(
              "Veuillez remplir les champs obligatoires", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final OrderB o =
        order != null ? order : new OrderB(orderNumber.getValue(), orderACombo.getValue());
    if (order != null) {
      o.setOrderNumber(orderNumber.getValue());
      o.setOrderA(orderACombo.getValue());
    }
    o.setOrderDate(orderDate.getValue());
    o.setExpectedDeliveryDate(expectedDeliveryDate.getValue());
    o.setNotes(notes.getValue().isBlank() ? null : notes.getValue());

    final OrderB saved = orderBService.save(o);

    if (order != null) {
      final var oldLines =
          documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
              DocumentLine.DocumentType.ORDER_B, saved.getId());
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
    orderBService.save(saved);

    Notification.show("Commande B enregistrée", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    onSave.run();
    close();
  }
}
