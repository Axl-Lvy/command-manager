package fr.axl.lvy.base.ui

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.applayout.AppLayout
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.Scroller
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.sidenav.SideNav
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Layout
import com.vaadin.flow.router.RouterLink
import com.vaadin.flow.server.menu.MenuConfiguration
import com.vaadin.flow.server.menu.MenuEntry
import com.vaadin.flow.theme.lumo.LumoUtility

/**
 * Application shell with a side navigation drawer. Menu items are auto-populated from `@Menu`
 * annotations on views.
 */
@Layout
@Suppress("unused")
class MainLayout : AppLayout() {

  init {
    setPrimarySection(Section.DRAWER)
    addToDrawer(createHeader(), Scroller(createSideNav()))
  }

  private fun createHeader(): Component {
    val appLogo = VaadinIcon.CUBES.create()
    appLogo.setSize("48px")
    appLogo.color = "green"

    val appName = Span("Gestion Commandes")
    appName.style.setFontWeight(Style.FontWeight.BOLD)

    val header = VerticalLayout(appLogo, appName)
    header.alignItems = FlexComponent.Alignment.CENTER

    val link = RouterLink("", HomeView::class.java)
    link.add(header)
    link.style["text-decoration"] = "none"
    link.style["color"] = "inherit"
    return link
  }

  private fun createSideNav(): SideNav {
    val nav = SideNav()
    nav.addClassNames(LumoUtility.Margin.Horizontal.MEDIUM)
    val parentItems = linkedMapOf<String, SideNavItem>()
    MenuConfiguration.getMenuEntries().forEach { menuEntry ->
      val groupedTitle = parseGroupedTitle(menuEntry.title())
      if (groupedTitle == null) {
        nav.addItem(createSideNavItem(menuEntry))
      } else {
        val parentItem =
          parentItems.getOrPut(groupedTitle.parent) { SideNavItem(groupedTitle.parent) }
        if (parentItem.element.parent == null) {
          nav.addItem(parentItem)
        }
        parentItem.addItem(createSideNavItem(menuEntry, groupedTitle.child))
      }
    }
    return nav
  }

  private fun createSideNavItem(
    menuEntry: MenuEntry,
    title: String = menuEntry.title(),
  ): SideNavItem =
    if (menuEntry.icon() != null) {
      SideNavItem(title, menuEntry.path(), Icon(menuEntry.icon()))
    } else {
      SideNavItem(title, menuEntry.path())
    }

  private fun parseGroupedTitle(title: String): GroupedTitle? {
    val segments = title.split("/", limit = 2).map { it.trim() }
    if (segments.size < 2 || segments.any { it.isBlank() }) {
      return null
    }
    return GroupedTitle(parent = segments[0], child = segments[1])
  }

  private data class GroupedTitle(val parent: String, val child: String)
}
