package fr.axl.lvy.quote.ui;

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
import fr.axl.lvy.product.ProductService;
import fr.axl.lvy.quote.Quote;
import fr.axl.lvy.quote.QuoteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

final class QuoteFormDialog extends Dialog {

  private final QuoteService quoteService;
  private final DocumentLineRepository documentLineRepository;
  private final Runnable onSave;
  private final @Nullable Quote quote;

  private final TextField quoteNumber = new TextField("N° Devis");
  private final ComboBox<Client> clientCombo = new ComboBox<>("Client");
  private final DatePicker quoteDate = new DatePicker("Date devis");
  private final DatePicker validityDate = new DatePicker("Date validité");
  private final TextField clientReference = new TextField("Réf. client");
  private final TextField subject = new TextField("Objet");
  private final TextField currency = new TextField("Devise");
  private final BigDecimalField exchangeRate = new BigDecimalField("Taux de change");
  private final TextField incoterms = new TextField("Incoterms");
  private final TextArea billingAddress = new TextArea("Adresse facturation");
  private final TextArea shippingAddress = new TextArea("Adresse livraison");
  private final TextArea notes = new TextArea("Notes");
  private final TextArea conditions = new TextArea("Conditions");
  private final DocumentLineEditor lineEditor;

  QuoteFormDialog(
      final QuoteService quoteService,
      final ClientService clientService,
      final ProductService productService,
      final DocumentLineRepository documentLineRepository,
      final @Nullable Quote quote,
      final Runnable onSave) {
    this.quoteService = quoteService;
    this.documentLineRepository = documentLineRepository;
    this.quote = quote;
    this.onSave = onSave;

    setHeaderTitle(quote == null ? "Nouveau devis" : "Modifier devis");
    setWidth("900px");
    setHeight("90%");

    quoteNumber.setRequired(true);
    clientCombo.setRequired(true);
    quoteDate.setRequired(true);

    clientCombo.setItems(clientService.findAll());
    clientCombo.setItemLabelGenerator(c -> c.getClientCode() + " - " + c.getName());

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 3));
    form.add(quoteNumber, clientCombo, quoteDate);
    form.add(validityDate, clientReference, subject);
    form.add(currency, exchangeRate, incoterms);
    form.add(billingAddress, 3);
    form.add(shippingAddress, 3);
    form.add(notes, 3);
    form.add(conditions, 3);

    lineEditor = new DocumentLineEditor(productService, DocumentLine.DocumentType.QUOTE);

    final var content = new VerticalLayout(form, lineEditor);
    content.setPadding(false);
    add(content);

    final var saveBtn = new Button("Enregistrer", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());
    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (quote != null) {
      populateForm(quote);
    } else {
      quoteDate.setValue(LocalDate.now());
      currency.setValue("EUR");
      exchangeRate.setValue(BigDecimal.ONE);
    }
  }

  private void populateForm(final Quote q) {
    quoteNumber.setValue(q.getQuoteNumber());
    clientCombo.setValue(q.getClient());
    quoteDate.setValue(q.getQuoteDate());
    validityDate.setValue(q.getValidityDate());
    clientReference.setValue(q.getClientReference() != null ? q.getClientReference() : "");
    subject.setValue(q.getSubject() != null ? q.getSubject() : "");
    currency.setValue(q.getCurrency());
    exchangeRate.setValue(q.getExchangeRate());
    incoterms.setValue(q.getIncoterms() != null ? q.getIncoterms() : "");
    billingAddress.setValue(q.getBillingAddress() != null ? q.getBillingAddress() : "");
    shippingAddress.setValue(q.getShippingAddress() != null ? q.getShippingAddress() : "");
    notes.setValue(q.getNotes() != null ? q.getNotes() : "");
    conditions.setValue(q.getConditions() != null ? q.getConditions() : "");

    final var lines =
        documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
            DocumentLine.DocumentType.QUOTE, q.getId());
    lineEditor.setLines(lines);
  }

  private void save() {
    if (quoteNumber.isEmpty() || clientCombo.isEmpty() || quoteDate.isEmpty()) {
      Notification.show(
              "Veuillez remplir les champs obligatoires", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final Quote q =
        quote != null
            ? quote
            : new Quote(quoteNumber.getValue(), clientCombo.getValue(), quoteDate.getValue());
    if (quote != null) {
      q.setQuoteNumber(quoteNumber.getValue());
      q.setClient(clientCombo.getValue());
      q.setQuoteDate(quoteDate.getValue());
    }
    q.setValidityDate(validityDate.getValue());
    q.setClientReference(clientReference.getValue().isBlank() ? null : clientReference.getValue());
    q.setSubject(subject.getValue().isBlank() ? null : subject.getValue());
    q.setCurrency(currency.getValue().isBlank() ? "EUR" : currency.getValue());
    q.setExchangeRate(exchangeRate.getValue() != null ? exchangeRate.getValue() : BigDecimal.ONE);
    q.setIncoterms(incoterms.getValue().isBlank() ? null : incoterms.getValue());
    q.setBillingAddress(billingAddress.getValue().isBlank() ? null : billingAddress.getValue());
    q.setShippingAddress(shippingAddress.getValue().isBlank() ? null : shippingAddress.getValue());
    q.setNotes(notes.getValue().isBlank() ? null : notes.getValue());
    q.setConditions(conditions.getValue().isBlank() ? null : conditions.getValue());

    final Quote saved = quoteService.save(q);

    // Delete old lines and save new ones
    if (quote != null) {
      final var oldLines =
          documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
              DocumentLine.DocumentType.QUOTE, saved.getId());
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
    quoteService.save(saved);

    Notification.show("Devis enregistré", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    onSave.run();
    close();
  }
}
