package fr.axl.lvy.documentline.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal

class DocumentLineEditor(
  private val productService: ProductService,
  private val documentType: DocumentLine.DocumentType,
) : VerticalLayout() {

  private val lines = mutableListOf<DocumentLine>()
  private val grid: Grid<DocumentLine>

  init {
    isPadding = false
    isSpacing = false

    grid = Grid()
    grid.addColumn(DocumentLine::designation).setHeader("Désignation").setFlexGrow(1)
    grid.addColumn(DocumentLine::quantity).setHeader("Qté").setAutoWidth(true)
    grid.addColumn(DocumentLine::unit).setHeader("Unité").setAutoWidth(true)
    grid.addColumn(DocumentLine::unitPriceExclTax).setHeader("PU HT").setAutoWidth(true)
    grid.addColumn(DocumentLine::discountPercent).setHeader("Remise %").setAutoWidth(true)
    grid.addColumn(DocumentLine::vatRate).setHeader("TVA %").setAutoWidth(true)
    grid.addColumn(DocumentLine::lineTotalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid
      .addComponentColumn { line ->
        val deleteBtn =
          Button("✕") {
            lines.remove(line)
            grid.setItems(lines)
          }
        deleteBtn.addThemeVariants(
          ButtonVariant.LUMO_SMALL,
          ButtonVariant.LUMO_ERROR,
          ButtonVariant.LUMO_TERTIARY,
        )
        deleteBtn
      }
      .setHeader("")
      .setAutoWidth(true)
    grid.setHeight("250px")

    val fromProductCombo = ComboBox<Product>("Ajouter depuis produit")
    fromProductCombo.setItems(productService.findActive())
    fromProductCombo.setItemLabelGenerator { "${it.reference} - ${it.designation}" }
    fromProductCombo.setWidthFull()
    fromProductCombo.addValueChangeListener { e ->
      if (e.value != null) {
        addLineFromProduct(e.value)
        fromProductCombo.clear()
      }
    }

    val addFreeLineBtn = Button("Ajouter ligne libre") { addFreeLine() }

    val toolbar = HorizontalLayout(fromProductCombo, addFreeLineBtn)
    toolbar.setWidthFull()
    toolbar.alignItems = FlexComponent.Alignment.BASELINE
    toolbar.setFlexGrow(1.0, fromProductCombo)

    add(toolbar, grid)
  }

  private fun addLineFromProduct(product: Product) {
    val line = DocumentLine.fromProduct(documentType, 0L, product)
    line.position = lines.size
    lines.add(line)
    grid.setItems(lines)
  }

  private fun addFreeLine() {
    val line = DocumentLine(documentType, 0L, "Nouvelle ligne")
    line.quantity = BigDecimal.ONE
    line.unitPriceExclTax = BigDecimal.ZERO
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal.ZERO
    line.position = lines.size
    line.recalculate()
    lines.add(line)
    grid.setItems(lines)
  }

  fun getLines(): List<DocumentLine> = ArrayList(lines)

  fun setLines(existing: List<DocumentLine>) {
    lines.clear()
    lines.addAll(existing)
    grid.setItems(lines)
  }
}
