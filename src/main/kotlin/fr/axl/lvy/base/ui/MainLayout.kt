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
import com.vaadin.flow.server.menu.MenuConfiguration
import com.vaadin.flow.server.menu.MenuEntry
import com.vaadin.flow.theme.lumo.LumoUtility

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
    return header
  }

  private fun createSideNav(): SideNav {
    val nav = SideNav()
    nav.addClassNames(LumoUtility.Margin.Horizontal.MEDIUM)
    MenuConfiguration.getMenuEntries().forEach { nav.addItem(createSideNavItem(it)) }
    return nav
  }

  private fun createSideNavItem(menuEntry: MenuEntry): SideNavItem =
      if (menuEntry.icon() != null) {
        SideNavItem(menuEntry.title(), menuEntry.path(), Icon(menuEntry.icon()))
      } else {
        SideNavItem(menuEntry.title(), menuEntry.path())
      }
}
