package fr.axl.lvy.base.ui

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouterLink
import com.vaadin.flow.theme.lumo.LumoUtility
import fr.axl.lvy.client.ui.ClientListView
import fr.axl.lvy.order.ui.CommandAListView
import fr.axl.lvy.order.ui.CommandBListView
import fr.axl.lvy.product.ui.ProductListView
import fr.axl.lvy.sale.ui.SalesAListView
import fr.axl.lvy.sale.ui.SalesBListView

@Route("")
@PageTitle("Accueil")
internal class HomeView : VerticalLayout() {

  init {
    setSizeFull()
    addClassNames(LumoUtility.Padding.LARGE)
    isSpacing = true
    alignItems = FlexComponent.Alignment.CENTER
    justifyContentMode = FlexComponent.JustifyContentMode.CENTER

    val desc =
      Paragraph(
        "Application de gestion des commandes clients et fournisseurs. Gérez vos produits, clients, ventes et commandes depuis un seul endroit."
      )
    desc.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE)
    desc.style["max-width"] = "500px"
    desc.style["text-align"] = "center"

    val row1 =
      row(
        simpleTile("Produits", "vaadin:package", ProductListView::class.java),
        simpleTile("Clients", "vaadin:users", ClientListView::class.java),
      )
    val row2 =
      row(
        groupTile(
          "Ventes",
          "vaadin:cart",
          "A" to SalesAListView::class.java,
          "B" to SalesBListView::class.java,
        ),
        groupTile(
          "Commandes",
          "vaadin:clipboard-text",
          "A" to CommandAListView::class.java,
          "B" to CommandBListView::class.java,
        ),
      )

    val grid = VerticalLayout(row1, row2)
    grid.isSpacing = false
    grid.isPadding = false
    grid.addClassNames(LumoUtility.Gap.LARGE)
    grid.alignItems = FlexComponent.Alignment.CENTER

    add(desc, grid)
  }

  private fun row(vararg components: Component): HorizontalLayout {
    val row = HorizontalLayout(*components)
    row.isSpacing = false
    row.isPadding = false
    row.addClassNames(LumoUtility.Gap.LARGE)
    return row
  }

  private fun simpleTile(
    title: String,
    iconName: String,
    target: Class<out Component>,
  ): RouterLink {
    val icon = Icon(iconName)
    icon.setSize("48px")
    icon.addClassNames(LumoUtility.TextColor.PRIMARY)

    val label = H2(title)
    label.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.XLARGE)

    val card = VerticalLayout(icon, label)
    card.alignItems = FlexComponent.Alignment.CENTER
    card.justifyContentMode = FlexComponent.JustifyContentMode.CENTER
    card.isSpacing = true
    card.isPadding = true
    card.setWidth("200px")
    card.setHeight("200px")
    card.addClassNames(
      LumoUtility.Background.CONTRAST_5,
      LumoUtility.BorderRadius.LARGE,
      LumoUtility.Padding.LARGE,
    )

    val link = RouterLink("", target)
    link.add(card)
    link.style["text-decoration"] = "none"
    link.style["color"] = "inherit"
    return link
  }

  private fun groupTile(
    title: String,
    iconName: String,
    vararg subLinks: Pair<String, Class<out Component>>,
  ): VerticalLayout {
    val icon = Icon(iconName)
    icon.setSize("36px")
    icon.addClassNames(LumoUtility.TextColor.PRIMARY)

    val label = H3(title)
    label.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.LARGE)

    val header = HorizontalLayout(icon, label)
    header.alignItems = FlexComponent.Alignment.CENTER
    header.isSpacing = true
    header.isPadding = true
    header.setWidthFull()
    header.justifyContentMode = FlexComponent.JustifyContentMode.CENTER
    header.addClassNames(LumoUtility.Border.BOTTOM, LumoUtility.Padding.MEDIUM)

    val columnsRow = HorizontalLayout()
    columnsRow.isSpacing = false
    columnsRow.isPadding = false
    columnsRow.setWidthFull()
    columnsRow.style["flex"] = "1"

    subLinks.forEachIndexed { index, (text, target) ->
      val link = RouterLink(text, target)
      link.style["flex"] = "1"
      link.style["display"] = "flex"
      link.style["align-items"] = "center"
      link.style["justify-content"] = "center"
      link.style["text-decoration"] = "none"
      link.style["font-weight"] = "bold"
      link.style["font-size"] = "var(--lumo-font-size-xl)"
      link.style["color"] = "var(--lumo-primary-text-color)"
      if (index > 0) link.style["border-left"] = "1px solid var(--lumo-contrast-10pct)"
      columnsRow.add(link)
      columnsRow.setFlexGrow(1.0, link)
    }

    val tile = VerticalLayout(header, columnsRow)
    tile.isSpacing = false
    tile.isPadding = false
    tile.setWidth("200px")
    tile.setHeight("200px")
    tile.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.LARGE)
    tile.style["overflow"] = "hidden"
    return tile
  }
}
