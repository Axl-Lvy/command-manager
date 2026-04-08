package fr.axl.lvy.sale

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "sales_b")
class SalesB(
  @field:NotBlank
  @Column(name = "sale_number", nullable = false, unique = true, length = 20)
  var saleNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_a_id", nullable = false)
  var salesA: SalesA,
) : SoftDeletableEntity() {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: SalesBStatus = SalesBStatus.DRAFT

  @Column(name = "sale_date") var saleDate: LocalDate? = null

  @Column(name = "expected_delivery_date") var expectedDeliveryDate: LocalDate? = null

  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  var totalExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  var totalVat: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  var totalInclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(length = 10) var incoterms: String? = null

  @Column(name = "incoterm_location", length = 100) var incotermLocation: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_b_id") var orderB: OrderB? = null

  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
  }

  enum class SalesBStatus {
    DRAFT,
    VALIDATED,
    CANCELLED,
  }
}
