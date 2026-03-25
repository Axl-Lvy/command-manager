package fr.axl.lvy.client.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientService;
import fr.axl.lvy.client.contact.Contact;
import fr.axl.lvy.user.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ClientFormDialog extends Dialog {

  private final ClientService clientService;
  private final Runnable onSave;
  private final @Nullable Client client;

  private final TextField clientCode = new TextField("Code client");
  private final TextField name = new TextField("Nom");
  private final ComboBox<Client.ClientType> type = new ComboBox<>("Type");
  private final ComboBox<Client.ClientRole> role = new ComboBox<>("Rôle");
  private final ComboBox<User.Company> visibleCompany = new ComboBox<>("Société visible");
  private final TextField email = new TextField("Email");
  private final TextField phone = new TextField("Téléphone");
  private final TextField website = new TextField("Site web");
  private final TextField siret = new TextField("SIRET");
  private final TextField vatNumber = new TextField("N° TVA intra.");
  private final TextArea billingAddress = new TextArea("Adresse facturation");
  private final TextArea shippingAddress = new TextArea("Adresse livraison");
  private final IntegerField paymentDelay = new IntegerField("Délai paiement (jours)");
  private final TextField paymentMethod = new TextField("Mode de paiement");
  private final BigDecimalField defaultDiscount = new BigDecimalField("Remise par défaut (%)");
  private final ComboBox<Client.Status> status = new ComboBox<>("Statut");
  private final TextArea notes = new TextArea("Notes");

  private final List<Contact> contacts = new ArrayList<>();
  private final Grid<Contact> contactGrid = new Grid<>();

  ClientFormDialog(
      final ClientService clientService, final @Nullable Client client, final Runnable onSave) {
    this.clientService = clientService;
    this.client = client;
    this.onSave = onSave;

    setHeaderTitle(client == null ? "Nouveau client" : "Modifier client");
    setWidth("750px");
    setHeight("80%");

    type.setItems(Client.ClientType.values());
    type.setItemLabelGenerator(t -> t == Client.ClientType.COMPANY ? "Société" : "Particulier");
    role.setItems(Client.ClientRole.values());
    role.setItemLabelGenerator(
        r ->
            switch (r) {
              case CLIENT -> "Client";
              case PRODUCER -> "Producteur";
              case BOTH -> "Les deux";
            });
    visibleCompany.setItems(User.Company.values());
    status.setItems(Client.Status.values());
    status.setItemLabelGenerator(s -> s == Client.Status.ACTIVE ? "Actif" : "Inactif");

    clientCode.setRequired(true);
    name.setRequired(true);

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.add(clientCode, name);
    form.add(type, role);
    form.add(visibleCompany, status);
    form.add(email, phone);
    form.add(website, siret);
    form.add(vatNumber, paymentMethod);
    form.add(paymentDelay, defaultDiscount);
    form.add(billingAddress, 2);
    form.add(shippingAddress, 2);
    form.add(notes, 2);

    // Contacts section
    contactGrid.addColumn(Contact::getLastName).setHeader("Nom").setFlexGrow(1);
    contactGrid.addColumn(Contact::getFirstName).setHeader("Prénom").setAutoWidth(true);
    contactGrid.addColumn(Contact::getEmail).setHeader("Email").setAutoWidth(true);
    contactGrid.addColumn(Contact::getPhone).setHeader("Téléphone").setAutoWidth(true);
    contactGrid.addColumn(c -> c.getRole().name()).setHeader("Rôle").setAutoWidth(true);
    contactGrid.setHeight("200px");

    final var addContactBtn = new Button("Ajouter contact", e -> addContact());
    final var removeContactBtn = new Button("Supprimer", e -> removeSelectedContact());
    removeContactBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

    final var contactActions = new HorizontalLayout(addContactBtn, removeContactBtn);
    final var contactSection = new VerticalLayout(new H3("Contacts"), contactActions, contactGrid);
    contactSection.setPadding(false);
    contactSection.setSpacing(false);

    final var content = new VerticalLayout(form, contactSection);
    content.setPadding(false);
    add(content);

    final var saveBtn = new Button("Enregistrer", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());
    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (client != null) {
      populateForm(client);
    } else {
      type.setValue(Client.ClientType.COMPANY);
      role.setValue(Client.ClientRole.CLIENT);
      visibleCompany.setValue(User.Company.A);
      status.setValue(Client.Status.ACTIVE);
    }
  }

  private void populateForm(final Client c) {
    clientCode.setValue(c.getClientCode());
    name.setValue(c.getName());
    type.setValue(c.getType());
    role.setValue(c.getRole());
    visibleCompany.setValue(c.getVisibleCompany());
    email.setValue(c.getEmail() != null ? c.getEmail() : "");
    phone.setValue(c.getPhone() != null ? c.getPhone() : "");
    website.setValue(c.getWebsite() != null ? c.getWebsite() : "");
    siret.setValue(c.getSiret() != null ? c.getSiret() : "");
    vatNumber.setValue(c.getVatNumber() != null ? c.getVatNumber() : "");
    billingAddress.setValue(c.getBillingAddress() != null ? c.getBillingAddress() : "");
    shippingAddress.setValue(c.getShippingAddress() != null ? c.getShippingAddress() : "");
    paymentDelay.setValue(c.getPaymentDelay());
    paymentMethod.setValue(c.getPaymentMethod() != null ? c.getPaymentMethod() : "");
    defaultDiscount.setValue(c.getDefaultDiscount());
    status.setValue(c.getStatus());
    notes.setValue(c.getNotes() != null ? c.getNotes() : "");
    contacts.addAll(c.getContacts());
    contactGrid.setItems(contacts);
  }

  private void addContact() {
    final var dialog =
        new ContactFormDialog(
            null,
            contact -> {
              contacts.add(contact);
              contactGrid.setItems(contacts);
            });
    dialog.open();
  }

  private void removeSelectedContact() {
    contactGrid.getSelectedItems().forEach(contacts::remove);
    contactGrid.setItems(contacts);
  }

  private void save() {
    if (clientCode.isEmpty() || name.isEmpty()) {
      Notification.show(
              "Veuillez remplir les champs obligatoires", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final Client c = client != null ? client : new Client(clientCode.getValue(), name.getValue());
    if (client != null) {
      c.setClientCode(clientCode.getValue());
      c.setName(name.getValue());
    }
    c.setType(type.getValue());
    c.setRole(role.getValue());
    c.setVisibleCompany(visibleCompany.getValue());
    c.setEmail(email.getValue().isBlank() ? null : email.getValue());
    c.setPhone(phone.getValue().isBlank() ? null : phone.getValue());
    c.setWebsite(website.getValue().isBlank() ? null : website.getValue());
    c.setSiret(siret.getValue().isBlank() ? null : siret.getValue());
    c.setVatNumber(vatNumber.getValue().isBlank() ? null : vatNumber.getValue());
    c.setBillingAddress(billingAddress.getValue().isBlank() ? null : billingAddress.getValue());
    c.setShippingAddress(shippingAddress.getValue().isBlank() ? null : shippingAddress.getValue());
    c.setPaymentDelay(paymentDelay.getValue());
    c.setPaymentMethod(paymentMethod.getValue().isBlank() ? null : paymentMethod.getValue());
    c.setDefaultDiscount(
        defaultDiscount.getValue() != null ? defaultDiscount.getValue() : BigDecimal.ZERO);
    c.setStatus(status.getValue());
    c.setNotes(notes.getValue().isBlank() ? null : notes.getValue());

    // Sync contacts
    c.getContacts().clear();
    for (final var contact : contacts) {
      contact.setClient(c);
      c.getContacts().add(contact);
    }

    clientService.save(c);
    Notification.show("Client enregistré", 3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    onSave.run();
    close();
  }
}
