package fr.axl.lvy.sale

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.paymentterm.PaymentTerm
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "sales_a")
class SalesCodig(
  @field:NotBlank
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
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('DRAFT','VALIDATED','CANCELLED')")
  var status: SalesCodigStatus = SalesCodigStatus.DRAFT

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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_term_id")
  var paymentTerm: PaymentTerm? = null

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(length = 10) var incoterms: String? = null

  @Column(name = "incoterm_location", length = 100) var incotermLocation: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @Column(columnDefinition = "TEXT") var conditions: String? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_codig_id")
  var orderCodig: OrderCodig? = null

  @OneToOne(mappedBy = "salesCodig", fetch = FetchType.LAZY)
  var salesNetstone: SalesNetstone? = null

  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
  }

  enum class SalesCodigStatus {
    DRAFT,
    VALIDATED,
    CANCELLED,
  }
}
