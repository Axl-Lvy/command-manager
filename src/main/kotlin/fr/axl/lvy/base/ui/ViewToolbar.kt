package fr.axl.lvy.base.ui

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.applayout.DrawerToggle
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.theme.lumo.LumoUtility

class ViewToolbar(viewTitle: String?, vararg components: Component) :
  Composite<HorizontalLayout>() {

  init {
    val layout = content
    layout.isPadding = true
    layout.isWrap = true
    layout.setWidthFull()
    layout.addClassName(LumoUtility.Border.BOTTOM)

    val drawerToggle = DrawerToggle()
    drawerToggle.addClassNames(LumoUtility.Margin.NONE)

    val title = H1(viewTitle)
    title.addClassNames(
      LumoUtility.FontSize.XLARGE,
      LumoUtility.Margin.NONE,
      LumoUtility.FontWeight.LIGHT,
    )

    val toggleAndTitle = HorizontalLayout(drawerToggle, title)
    toggleAndTitle.defaultVerticalComponentAlignment = FlexComponent.Alignment.CENTER
    layout.add(toggleAndTitle)
    layout.setFlexGrow(1.0, toggleAndTitle)

    if (components.isNotEmpty()) {
      val actions = HorizontalLayout(*components)
      actions.justifyContentMode = FlexComponent.JustifyContentMode.BETWEEN
      layout.add(actions)
    }
  }

  companion object {
    fun group(vararg components: Component): Component {
      val group = HorizontalLayout(*components)
      group.isWrap = true
      return group
    }
  }
}
