package fr.axl.lvy.base.ui

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout

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

internal enum class DocumentFlowStep {
  SALES_CODIG,
  ORDER_CODIG,
  SALES_NETSTONE,
  ORDER_NETSTONE,
}

internal class DocumentFlowNavigator(navigation: DocumentFlowNavigation) : VerticalLayout() {

  init {
    isPadding = false
    isSpacing = false
    setWidthFull()
    defaultHorizontalComponentAlignment = FlexComponent.Alignment.STRETCH
    style.set("gap", "0.35rem")
    style.set("padding", "0.25rem 0 0.75rem 0")

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
        isPadding = false
        isSpacing = false
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
      style.set("text-align", "center")
      style.set("display", "flex")
      style.set("justify-content", "center")
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
      style.set("color", color)
      style.set("font-weight", "700")
      style.set("letter-spacing", "0.02em")
    }

  private fun disabledLabel(label: String, color: String): Component =
    Span(label).apply {
      style.set("color", color)
      style.set("opacity", "0.28")
      style.set("font-weight", "500")
    }

  private fun actionButton(label: String, color: String, action: Runnable): Component =
    Button(label) { action.run() }
      .apply {
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        style.set("color", color)
        style.set("opacity", "0.28")
        style.set("font-weight", "500")
        style.set("padding", "0")
        style.set("min-width", "0")
      }

  private fun buildSegmentRow(): Component =
    HorizontalLayout(segment(CODIG_COLOR), segment(NETSTONE_COLOR)).apply {
      isPadding = false
      isSpacing = false
      setWidthFull()
    }

  private fun segment(color: String): Component =
    Div().apply {
      setWidth("50%")
      style.set("height", "8px")
      style.set("background", color)
      style.set("border-radius", "999px")
    }

  private fun buildCompanyRow(): Component =
    HorizontalLayout(companyLabel("CoDIG"), companyLabel("Netstone")).apply {
      isPadding = false
      isSpacing = false
      setWidthFull()
    }

  private fun companyLabel(label: String): Component =
    Div().apply {
      setWidth("50%")
      style.set("text-align", "center")
      add(
        Span(label).apply {
          style.set("font-size", "0.8rem")
          style.set("font-weight", "600")
          style.set("color", "var(--lumo-secondary-text-color)")
        }
      )
    }

  companion object {
    private const val CODIG_COLOR = "#1d4ed8"
    private const val NETSTONE_COLOR = "#15803d"
  }
}
