package fr.axl.lvy.order.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fr.axl.lvy.base.ui.ViewToolbar;
import fr.axl.lvy.client.Client;
import fr.axl.lvy.client.ClientService;
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderAService;
import fr.axl.lvy.product.ProductService;
import java.util.Optional;

@Route("commandes-a")
@PageTitle("Commandes A")
@Menu(order = 3, icon = "vaadin:cart", title = "Commandes A")
class OrderAListView extends VerticalLayout {

  private final OrderAService orderAService;
  private final ClientService clientService;
  private final ProductService productService;
  private final DocumentLineRepository documentLineRepository;
  private final Grid<OrderA> grid;

  OrderAListView(
      final OrderAService orderAService,
      final ClientService clientService,
      final ProductService productService,
      final DocumentLineRepository documentLineRepository) {
    this.orderAService = orderAService;
    this.clientService = clientService;
    this.productService = productService;
    this.documentLineRepository = documentLineRepository;

    final var addBtn = new Button("Nouvelle commande", _ -> openForm(null));
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    grid = new Grid<>();
    grid.addColumn(OrderA::getOrderNumber).setHeader("N° Commande").setAutoWidth(true);
    grid.addColumn(order -> Optional.ofNullable(order.getClient()).map(Client::getName).orElse(""))
        .setHeader("Client")
        .setFlexGrow(1);
    grid.addColumn(OrderA::getOrderDate).setHeader("Date").setAutoWidth(true);
    grid.addColumn(OrderA::getTotalExclTax).setHeader("Total HT").setAutoWidth(true);
    grid.addColumn(OrderA::getTotalInclTax).setHeader("Total TTC").setAutoWidth(true);
    grid.addColumn(OrderA::getMarginExclTax).setHeader("Marge HT").setAutoWidth(true);
    grid.addColumn(order -> order.getStatus().name()).setHeader("Statut").setAutoWidth(true);
    grid.setEmptyStateText("Aucune commande A");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
    grid.addItemDoubleClickListener(event -> openForm(event.getItem()));

    refreshGrid();

    setSizeFull();
    setPadding(false);
    setSpacing(false);
    getStyle().setOverflow(Style.Overflow.HIDDEN);

    add(new ViewToolbar("Commandes A", addBtn));
    add(grid);
  }

  private void openForm(final OrderA order) {
    new OrderAFormDialog(
            orderAService,
            clientService,
            productService,
            documentLineRepository,
            order,
            this::refreshGrid)
        .open();
  }

  private void refreshGrid() {
    grid.setItems(orderAService.findAll());
  }
}
