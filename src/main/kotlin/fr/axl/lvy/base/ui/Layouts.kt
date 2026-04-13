package fr.axl.lvy.base.ui

import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style

/** Removes all internal padding and spacing from this layout. */
fun HorizontalLayout.noGap() {
  isPadding = false
  isSpacing = false
}

/** Removes all internal padding and spacing from this layout. */
fun VerticalLayout.noGap() {
  isPadding = false
  isSpacing = false
}

/**
 * Configures this layout as a full-height list-view container: fills parent size, removes padding
 * and spacing, and hides overflow so the inner grid scrolls independently.
 */
fun VerticalLayout.initAsListContainer() {
  setSizeFull()
  isPadding = false
  isSpacing = false
  style.setOverflow(Style.Overflow.HIDDEN)
}
