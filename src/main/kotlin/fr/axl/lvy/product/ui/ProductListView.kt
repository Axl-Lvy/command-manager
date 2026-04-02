package fr.axl.lvy.product.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService

@Route("produits")
@PageTitle("Produits")
@Menu(order = 0.0, icon = "vaadin:package", title = "Produits")
internal class ProductListView(
  private val productService: ProductService,
  private val clientService: ClientService,
) : VerticalLayout() {

  private val grid: Grid<Product>

  init {
    val addBtn = Button("Nouveau produit") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(Product::name).setHeader("Nom").setFlexGrow(1)
    grid.addColumn { it.type.name }.setHeader("Type").setAutoWidth(true)
    grid.addColumn(Product::sellingPriceExclTax).setHeader("Prix vente HT").setAutoWidth(true)
    grid.addColumn(Product::purchasePriceExclTax).setHeader("Prix achat HT").setAutoWidth(true)
    grid.addColumn { if (it.active) "Actif" else "Inactif" }.setHeader("Statut").setAutoWidth(true)
    grid
      .addComponentColumn { product ->
        val editButton = Button("Modifier") { openForm(product) }
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)

        val archiveButton =
          Button("Archiver") {
            product.active = false
            productService.save(product)
            refreshGrid()
          }
        archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY)
        archiveButton.isEnabled = product.active

        HorizontalLayout(editButton, archiveButton).apply {
          isPadding = false
          isSpacing = true
        }
      }
      .setHeader("Actions")
      .setAutoWidth(true)
      .setFlexGrow(0)
    grid.setEmptyStateText("Aucun produit")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Produits", addBtn))
    add(grid)
  }

  private fun openForm(product: Product?) {
    val loadedProduct = product?.id?.let { productService.findDetailedById(it).orElse(null) }
    ProductFormDialog(productService, clientService, loadedProduct, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(productService.findAll())
  }
}
