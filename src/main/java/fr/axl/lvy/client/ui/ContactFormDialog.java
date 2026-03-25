package fr.axl.lvy.client.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import fr.axl.lvy.client.contact.Contact;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class ContactFormDialog extends Dialog {

  private final @Nullable Contact contact;
  private final Consumer<Contact> onSave;

  private final TextField lastName = new TextField("Nom");
  private final TextField firstName = new TextField("Prénom");
  private final TextField email = new TextField("Email");
  private final TextField phone = new TextField("Téléphone");
  private final TextField mobile = new TextField("Mobile");
  private final TextField jobTitle = new TextField("Fonction");
  private final ComboBox<Contact.ContactRole> role = new ComboBox<>("Rôle");
  private final Checkbox active = new Checkbox("Actif");

  ContactFormDialog(final @Nullable Contact contact, final Consumer<Contact> onSave) {
    this.contact = contact;
    this.onSave = onSave;

    setHeaderTitle(contact == null ? "Nouveau contact" : "Modifier contact");
    setWidth("500px");

    lastName.setRequired(true);
    role.setItems(Contact.ContactRole.values());
    role.setItemLabelGenerator(
        r ->
            switch (r) {
              case PRIMARY -> "Principal";
              case BILLING -> "Facturation";
              case TECHNICAL -> "Technique";
              case OTHER -> "Autre";
            });

    final var form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.add(lastName, firstName);
    form.add(email, phone);
    form.add(mobile, jobTitle);
    form.add(role, active);
    add(form);

    final var saveBtn = new Button("OK", e -> save());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    final var cancelBtn = new Button("Annuler", e -> close());
    getFooter().add(new HorizontalLayout(saveBtn, cancelBtn));

    if (contact != null) {
      populateForm(contact);
    } else {
      role.setValue(Contact.ContactRole.OTHER);
      active.setValue(true);
    }
  }

  private void populateForm(final Contact c) {
    lastName.setValue(c.getLastName());
    firstName.setValue(c.getFirstName() != null ? c.getFirstName() : "");
    email.setValue(c.getEmail() != null ? c.getEmail() : "");
    phone.setValue(c.getPhone() != null ? c.getPhone() : "");
    mobile.setValue(c.getMobile() != null ? c.getMobile() : "");
    jobTitle.setValue(c.getJobTitle() != null ? c.getJobTitle() : "");
    role.setValue(c.getRole());
    active.setValue(c.isActive());
  }

  private void save() {
    if (lastName.isEmpty()) {
      Notification.show("Le nom est obligatoire", 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    final Contact c = contact != null ? contact : new Contact(null, lastName.getValue());
    if (contact != null) {
      c.setLastName(lastName.getValue());
    }
    c.setFirstName(firstName.getValue().isBlank() ? null : firstName.getValue());
    c.setEmail(email.getValue().isBlank() ? null : email.getValue());
    c.setPhone(phone.getValue().isBlank() ? null : phone.getValue());
    c.setMobile(mobile.getValue().isBlank() ? null : mobile.getValue());
    c.setJobTitle(jobTitle.getValue().isBlank() ? null : jobTitle.getValue());
    c.setRole(role.getValue());
    c.setActive(active.getValue());

    onSave.accept(c);
    close();
  }
}
