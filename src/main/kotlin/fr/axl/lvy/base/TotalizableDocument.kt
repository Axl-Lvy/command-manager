package fr.axl.lvy.base

import fr.axl.lvy.documentline.DocumentLine
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.math.BigDecimal

@MappedSuperclass
abstract class TotalizableDocument : SoftDeletableEntity() {
  @Column(name = "expected_delivery_date") var expectedDeliveryDate: java.time.LocalDate? = null

  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  var totalExclTax: BigDecimal = BigDecimal.ZERO
    protected set

  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  var totalVat: BigDecimal = BigDecimal.ZERO
    protected set

  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  var totalInclTax: BigDecimal = BigDecimal.ZERO
    protected set

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(length = 10) var incoterms: String? = null

  @Column(name = "incoterm_location", length = 100) var incotermLocation: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
  }
}
