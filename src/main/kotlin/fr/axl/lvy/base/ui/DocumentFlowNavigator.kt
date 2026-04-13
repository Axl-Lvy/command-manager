package fr.axl.lvy.base.ui

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout

/**
 * Holds the current step and optional click actions for the four-stage document flow: Codig sale →
 * Codig order → Netstone sale → Netstone order. A null action means that step has no linked
 * document yet and is rendered as disabled.
 */
internal data class DocumentFlowNavigation(
  val currentStep: DocumentFlowStep,
  val openSalesCodig: Runnable? = null,
  val openOrderCodig: Runnable? = null,
  val openSalesNetstone: Runnable? = null,
  val openOrderNetstone: Runnable? = null,
) {
  fun hasLinks(): Boolean =
    openSalesCodig != null ||
      openOrderCodig != null ||
      openSalesNetstone != null ||
      openOrderNetstone != null
}

/** The four stages in the Codig→Netstone document chain. */
internal enum class DocumentFlowStep {
  SALES_CODIG,
  ORDER_CODIG,
  SALES_NETSTONE,
  ORDER_NETSTONE,
}

/**
 * Visual step navigator for the four-stage document flow (Codig sale / order → Netstone sale /
 * order). The current step is bold; linked steps are clickable buttons; unlinked steps are dimmed.
 */
internal class DocumentFlowNavigator(navigation: DocumentFlowNavigation) : VerticalLayout() {

  init {
    noGap()
    setWidthFull()
    defaultHorizontalComponentAlignment = FlexComponent.Alignment.STRETCH
    style["gap"] = "0.35rem"
    style["padding"] = "0.25rem 0 0.75rem 0"

    add(buildStepRow(navigation), buildSegmentRow(), buildCompanyRow())
  }

  private fun buildStepRow(navigation: DocumentFlowNavigation): Component =
    HorizontalLayout(
        buildStepCell(
          "Vente",
          CODIG_COLOR,
          navigation.currentStep == DocumentFlowStep.SALES_CODIG,
          navigation.openSalesCodig,
        ),
        buildStepCell(
          "Achat",
          CODIG_COLOR,
          navigation.currentStep == DocumentFlowStep.ORDER_CODIG,
          navigation.openOrderCodig,
        ),
        buildStepCell(
          "Vente",
          NETSTONE_COLOR,
          navigation.currentStep == DocumentFlowStep.SALES_NETSTONE,
          navigation.openSalesNetstone,
        ),
        buildStepCell(
          "Achat",
          NETSTONE_COLOR,
          navigation.currentStep == DocumentFlowStep.ORDER_NETSTONE,
          navigation.openOrderNetstone,
        ),
      )
      .apply {
        noGap()
        setWidthFull()
      }

  private fun buildStepCell(
    label: String,
    color: String,
    current: Boolean,
    action: Runnable?,
  ): Component =
    Div().apply {
      setWidth("25%")
      style["text-align"] = "center"
      style["display"] = "flex"
      style["justify-content"] = "center"
      add(
        when {
          current -> currentLabel(label, color)
          action != null -> actionButton(label, color, action)
          else -> disabledLabel(label, color)
        }
      )
    }

  private fun currentLabel(label: String, color: String): Component =
    Span(label).apply {
      style["color"] = color
      style[STYLE_FONT_WEIGHT] = "700"
      style["letter-spacing"] = "0.02em"
    }

  private fun disabledLabel(label: String, color: String): Component =
    Span(label).apply {
      style["color"] = color
      style["opacity"] = "0.28"
      style[STYLE_FONT_WEIGHT] = "500"
    }

  private fun actionButton(label: String, color: String, action: Runnable): Component =
    Button(label) { action.run() }
      .apply {
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        style["color"] = color
        style["opacity"] = "0.28"
        style[STYLE_FONT_WEIGHT] = "500"
        style["padding"] = "0"
        style["min-width"] = "0"
      }

  private fun buildSegmentRow(): Component =
    HorizontalLayout(segment(CODIG_COLOR), segment(NETSTONE_COLOR)).apply {
      noGap()
      setWidthFull()
    }

  private fun segment(color: String): Component =
    Div().apply {
      setWidth("50%")
      style["height"] = "8px"
      style["background"] = color
      style["border-radius"] = "999px"
    }

  private fun buildCompanyRow(): Component =
    HorizontalLayout(companyLabel("CoDIG"), companyLabel("Netstone")).apply {
      noGap()
      setWidthFull()
    }

  private fun companyLabel(label: String): Component =
    Div().apply {
      setWidth("50%")
      style["text-align"] = "center"
      add(
        Span(label).apply {
          style["font-size"] = "0.8rem"
          style[STYLE_FONT_WEIGHT] = "600"
          style["color"] = "var(--lumo-secondary-text-color)"
        }
      )
    }

  companion object {
    private const val CODIG_COLOR = "#1d4ed8"
    private const val NETSTONE_COLOR = "#15803d"
    private const val STYLE_FONT_WEIGHT = "font-weight"
  }
}
