package fr.axl.lvy.documentline.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.product.Product;
import fr.axl.lvy.product.ProductService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class DocumentLineEditor extends VerticalLayout {

  private final ProductService productService;
  private final DocumentLine.DocumentType documentType;
  private final List<DocumentLine> lines = new ArrayList<>();
  private final Grid<DocumentLine> grid;

  public DocumentLineEditor(
      final ProductService productService, final DocumentLine.DocumentType documentType) {
    this.productService = productService;
    this.documentType = documentType;
    setPadding(false);
    setSpacing(false);

    grid = new Grid<>();
    grid.addColumn(DocumentLine::getDesignation).setHeader("Désignation").setFlexGrow(1);
    grid.addColumn(DocumentLine::getQuantity).setHeader("Qté").setAutoWidth(true);
    grid.addColumn(DocumentLine::getUnit).setHeader("Unité").setAutoWidth(true);
    grid.addColumn(DocumentLine::getUnitPriceExclTax).setHeader("PU HT").setAutoWidth(true);
    grid.addColumn(DocumentLine::getDiscountPercent).setHeader("Remise %").setAutoWidth(true);
    grid.addColumn(DocumentLine::getVatRate).setHeader("TVA %").setAutoWidth(true);
    grid.addColumn(DocumentLine::getLineTotalExclTax).setHeader("Total HT").setAutoWidth(true);
    grid.addComponentColumn(
            line -> {
              final var deleteBtn =
                  new Button(
                      "✕",
                      e -> {
                        lines.remove(line);
                        grid.setItems(lines);
                      });
              deleteBtn.addThemeVariants(
                  ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
              return deleteBtn;
            })
        .setHeader("")
        .setAutoWidth(true);
    grid.setHeight("250px");

    final var fromProductCombo = new ComboBox<Product>("Ajouter depuis produit");
    fromProductCombo.setItems(productService.findActive());
    fromProductCombo.setItemLabelGenerator(p -> p.getReference() + " - " + p.getDesignation());
    fromProductCombo.setWidthFull();
    fromProductCombo.addValueChangeListener(
        e -> {
          if (e.getValue() != null) {
            addLineFromProduct(e.getValue());
            fromProductCombo.clear();
          }
        });

    final var addFreeLineBtn = new Button("Ajouter ligne libre", e -> addFreeLine());

    final var toolbar = new HorizontalLayout(fromProductCombo, addFreeLineBtn);
    toolbar.setWidthFull();
    toolbar.setAlignItems(Alignment.BASELINE);
    toolbar.setFlexGrow(1, fromProductCombo);

    add(toolbar, grid);
  }

  private void addLineFromProduct(final Product product) {
    final var line = DocumentLine.fromProduct(documentType, 0L, product);
    line.setPosition(lines.size());
    lines.add(line);
    grid.setItems(lines);
  }

  private void addFreeLine() {
    final var line = new DocumentLine(documentType, 0L, "Nouvelle ligne");
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPriceExclTax(BigDecimal.ZERO);
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(BigDecimal.ZERO);
    line.setPosition(lines.size());
    line.recalculate();
    lines.add(line);
    grid.setItems(lines);
  }

  public List<DocumentLine> getLines() {
    return new ArrayList<>(lines);
  }

  public void setLines(final List<DocumentLine> existing) {
    lines.clear();
    lines.addAll(existing);
    grid.setItems(lines);
  }
}
