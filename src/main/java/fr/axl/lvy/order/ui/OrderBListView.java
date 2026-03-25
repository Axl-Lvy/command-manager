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
import fr.axl.lvy.documentline.DocumentLineRepository;
import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderAService;
import fr.axl.lvy.order.OrderB;
import fr.axl.lvy.order.OrderBService;
import fr.axl.lvy.product.ProductService;
import java.util.Optional;

@Route("commandes-b")
@PageTitle("Commandes B")
@Menu(order = 4, icon = "vaadin:truck", title = "Commandes B")
class OrderBListView extends VerticalLayout {

  private final OrderBService orderBService;
  private final OrderAService orderAService;
  private final ProductService productService;
  private final DocumentLineRepository documentLineRepository;
  private final Grid<OrderB> grid;

  OrderBListView(
      final OrderBService orderBService,
      final OrderAService orderAService,
      final ProductService productService,
      final DocumentLineRepository documentLineRepository) {
    this.orderBService = orderBService;
    this.orderAService = orderAService;
    this.productService = productService;
    this.documentLineRepository = documentLineRepository;

    final var addBtn = new Button("Nouvelle commande B", event -> openForm(null));
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    grid = new Grid<>();
    grid.addColumn(OrderB::getOrderNumber).setHeader("N° Commande B").setAutoWidth(true);
    grid.addColumn(
            order -> Optional.ofNullable(order.getOrderA()).map(OrderA::getOrderNumber).orElse(""))
        .setHeader("Commande A liée")
        .setAutoWidth(true);
    grid.addColumn(OrderB::getOrderDate).setHeader("Date").setAutoWidth(true);
    grid.addColumn(OrderB::getTotalExclTax).setHeader("Total HT").setAutoWidth(true);
    grid.addColumn(OrderB::getTotalInclTax).setHeader("Total TTC").setAutoWidth(true);
    grid.addColumn(order -> order.getStatus().name()).setHeader("Statut").setAutoWidth(true);
    grid.setEmptyStateText("Aucune commande B");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
    grid.addItemDoubleClickListener(event -> openForm(event.getItem()));

    refreshGrid();

    setSizeFull();
    setPadding(false);
    setSpacing(false);
    getStyle().setOverflow(Style.Overflow.HIDDEN);

    add(new ViewToolbar("Commandes B", addBtn));
    add(grid);
  }

  private void openForm(final OrderB order) {
    new OrderBFormDialog(
            orderBService,
            orderAService,
            productService,
            documentLineRepository,
            order,
            this::refreshGrid)
        .open();
  }

  private void refreshGrid() {
    grid.setItems(orderBService.findAll());
  }
}
