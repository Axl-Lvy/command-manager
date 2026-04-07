package fr.axl.lvy.sale

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderA
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "sales_a")
class SalesA(
  @NotBlank
  @Column(name = "sale_number", nullable = false, unique = true, length = 20)
  var saleNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @Column(name = "sale_date", nullable = false) var saleDate: LocalDate,
) : SoftDeletableEntity() {
  @Column(name = "client_reference", length = 100) var clientReference: String? = null

  var subject: String? = null

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: SalesAStatus = SalesAStatus.DRAFT

  @Column(name = "expected_delivery_date") var expectedDeliveryDate: LocalDate? = null

  @Column(name = "billing_address", columnDefinition = "TEXT") var billingAddress: String? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  var totalExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  var totalVat: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  var totalInclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  var vatRate: BigDecimal = BigDecimal.ZERO

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(length = 10) var incoterms: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @Column(columnDefinition = "TEXT") var conditions: String? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_a_id") var orderA: OrderA? = null

  @OneToOne(mappedBy = "salesA", fetch = FetchType.LAZY) var salesB: SalesB? = null

  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
  }

  enum class SalesAStatus {
    DRAFT,
    VALIDATED,
    CANCELLED,
  }
}
