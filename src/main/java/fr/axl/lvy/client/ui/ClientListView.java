package fr.axl.lvy.client.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fr.axl.lvy.base.ui.ViewToolbar;
import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientService;

@Route("clients")
@PageTitle("Clients")
@Menu(order = 1, icon = "vaadin:users", title = "Clients")
class ClientListView extends VerticalLayout {

  private final ClientService clientService;
  private final Grid<Client> grid;

  ClientListView(ClientService clientService) {
    this.clientService = clientService;

    var addBtn =
        new Button("Nouveau client", event -> Notification.show("TODO: formulaire client"));
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    grid = new Grid<>();
    grid.addColumn(Client::getClientCode).setHeader("Code").setAutoWidth(true);
    grid.addColumn(Client::getName).setHeader("Nom").setFlexGrow(1);
    grid.addColumn(client -> client.getType().name()).setHeader("Type").setAutoWidth(true);
    grid.addColumn(client -> client.getRole().name()).setHeader("Rôle").setAutoWidth(true);
    grid.addColumn(Client::getEmail).setHeader("Email").setAutoWidth(true);
    grid.addColumn(Client::getPhone).setHeader("Téléphone").setAutoWidth(true);
    grid.addColumn(client -> client.getStatus().name()).setHeader("Statut").setAutoWidth(true);
    grid.setEmptyStateText("Aucun client");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);

    refreshGrid();

    setSizeFull();
    setPadding(false);
    setSpacing(false);
    getStyle().setOverflow(Style.Overflow.HIDDEN);

    add(new ViewToolbar("Clients", addBtn));
    add(grid);
  }

  private void refreshGrid() {
    grid.setItems(clientService.findAll());
  }
}
