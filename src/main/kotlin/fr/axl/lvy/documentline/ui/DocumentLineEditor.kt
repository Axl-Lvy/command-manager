package fr.axl.lvy.documentline.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import java.math.BigDecimal

class DocumentLineEditor(
  private val productService: ProductService,
  private val documentType: DocumentLine.DocumentType,
  private val clientSupplier: (() -> Client?)? = null,
  private val currencySupplier: (() -> String?)? = null,
  private val currencyUpdater: ((String) -> Unit)? = null,
  private val usePurchasePrice: Boolean = false,
) : VerticalLayout() {

  private val lines = mutableListOf<DocumentLine>()
  private val grid: Grid<DocumentLine>

  init {
    isPadding = false
    isSpacing = false

    grid = Grid()
    grid
      .addComponentColumn { line ->
        TextField().apply {
          value = line.designation
          placeholder = "Désignation"
          width = "100%"
          valueChangeMode = ValueChangeMode.EAGER
          addValueChangeListener { line.designation = it.value }
        }
      }
      .setHeader("Désignation")
      .setFlexGrow(1)
    grid
      .addComponentColumn { line ->
        BigDecimalField().apply {
          value = line.quantity
          placeholder = "Qté"
          width = "110px"
          valueChangeMode = ValueChangeMode.EAGER
          addValueChangeListener {
            line.quantity = it.value ?: BigDecimal.ZERO
            line.recalculate()
            refreshGrid()
          }
        }
      }
      .setHeader("Qté")
      .setAutoWidth(true)
    grid
      .addComponentColumn { line ->
        TextField().apply {
          value = line.unit ?: ""
          placeholder = "Unité"
          width = "100px"
          valueChangeMode = ValueChangeMode.EAGER
          addValueChangeListener { line.unit = it.value.takeIf(String::isNotBlank) }
        }
      }
      .setHeader("Unité")
      .setAutoWidth(true)
    grid
      .addComponentColumn { line ->
        BigDecimalField().apply {
          value = line.unitPriceExclTax
          placeholder = "PU HT"
          width = "140px"
          valueChangeMode = ValueChangeMode.EAGER
          addValueChangeListener {
            line.unitPriceExclTax = it.value ?: BigDecimal.ZERO
            line.recalculate()
            refreshGrid()
          }
        }
      }
      .setHeader("PU HT")
      .setAutoWidth(true)
    if (currencySupplier != null && currencyUpdater != null) {
      grid
        .addComponentColumn {
          ComboBox<String>().apply {
            setItems("EUR", "$")
            value = currencySupplier.invoke() ?: "EUR"
            width = "100px"
            addValueChangeListener { event ->
              val currency = event.value ?: return@addValueChangeListener
              currencyUpdater.invoke(currency)
              refreshGrid()
            }
          }
        }
        .setHeader("Devise")
        .setAutoWidth(true)
    }
    grid
      .addComponentColumn { line ->
        BigDecimalField().apply {
          value = line.discountPercent
          placeholder = "Remise %"
          width = "120px"
          valueChangeMode = ValueChangeMode.EAGER
          addValueChangeListener {
            line.discountPercent = it.value ?: BigDecimal.ZERO
            line.recalculate()
            refreshGrid()
          }
        }
      }
      .setHeader("Remise %")
      .setAutoWidth(true)
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
    grid.addThemeVariants(GridVariant.LUMO_COMPACT)

    val fromProductCombo = ComboBox<Product>("Ajouter depuis produit")
    fromProductCombo.setItems(productService.findActive())
    fromProductCombo.setItemLabelGenerator { "${it.reference} - ${it.name}" }
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
    val line = DocumentLine.fromProduct(documentType, 0L, product, clientSupplier?.invoke())
    if (usePurchasePrice) {
      line.unitPriceExclTax = product.purchasePriceExclTax
      line.recalculate()
    }
    line.position = lines.size
    lines.add(line)
    refreshGrid()
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
    refreshGrid()
  }

  fun getLines(): List<DocumentLine> = ArrayList(lines)

  fun setLines(existing: List<DocumentLine>) {
    lines.clear()
    lines.addAll(existing)
    refreshGrid()
  }

  private fun refreshGrid() {
    grid.setItems(ArrayList(lines))
  }
}
