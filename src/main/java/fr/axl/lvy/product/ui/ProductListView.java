package fr.axl.lvy.product.ui;

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
import fr.axl.lvy.product.Product;
import fr.axl.lvy.product.ProductService;

@Route("produits")
@PageTitle("Produits")
@Menu(order = 0, icon = "vaadin:package", title = "Produits")
class ProductListView extends VerticalLayout {

  private final ProductService productService;
  private final Grid<Product> grid;

  ProductListView(final ProductService productService) {
    this.productService = productService;

    final var addBtn = new Button("Nouveau produit", event -> openForm(null));
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    grid = new Grid<>();
    grid.addColumn(Product::getReference).setHeader("Référence").setAutoWidth(true);
    grid.addColumn(Product::getDesignation).setHeader("Désignation").setFlexGrow(1);
    grid.addColumn(product -> product.getType().name()).setHeader("Type").setAutoWidth(true);
    grid.addColumn(Product::getSellingPriceExclTax).setHeader("Prix vente HT").setAutoWidth(true);
    grid.addColumn(Product::getPurchasePriceExclTax).setHeader("Prix achat HT").setAutoWidth(true);
    grid.addColumn(product -> product.isActive() ? "Actif" : "Inactif")
        .setHeader("Statut")
        .setAutoWidth(true);
    grid.setEmptyStateText("Aucun produit");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
    grid.addItemDoubleClickListener(event -> openForm(event.getItem()));

    refreshGrid();

    setSizeFull();
    setPadding(false);
    setSpacing(false);
    getStyle().setOverflow(Style.Overflow.HIDDEN);

    add(new ViewToolbar("Produits", addBtn));
    add(grid);
  }

  private void openForm(final Product product) {
    new ProductFormDialog(productService, product, this::refreshGrid).open();
  }

  private void refreshGrid() {
    grid.setItems(productService.findAll());
  }
}
