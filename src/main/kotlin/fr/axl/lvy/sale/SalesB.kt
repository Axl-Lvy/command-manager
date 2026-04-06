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
  @NotBlank
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

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_b_id") var orderB: OrderB? = null

  fun recalculateTotals(lines: List<DocumentLine>) {
    totalExclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
    totalVat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
    totalInclTax = totalExclTax.add(totalVat)
  }

  enum class SalesBStatus {
    DRAFT,
    VALIDATED,
    CANCELLED,
  }
}
