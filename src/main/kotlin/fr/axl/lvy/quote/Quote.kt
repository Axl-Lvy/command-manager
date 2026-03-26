package fr.axl.lvy.quote

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "quotes")
class Quote(
  @NotBlank
  @Column(name = "quote_number", nullable = false, unique = true, length = 20)
  var quoteNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @Column(name = "quote_date", nullable = false) var quoteDate: LocalDate,
) : SoftDeletableEntity() {
  @Column(name = "client_reference", length = 100) var clientReference: String? = null

  var subject: String? = null

  @Enumerated(EnumType.STRING) @Column(nullable = false) var status: QuoteStatus = QuoteStatus.DRAFT

  @Column(name = "validity_date") var validityDate: LocalDate? = null

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

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @Column(length = 10) var incoterms: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @Column(columnDefinition = "TEXT") var conditions: String? = null

  fun isEditable(): Boolean = status == QuoteStatus.DRAFT || status == QuoteStatus.SENT

  fun recalculateTotals(lines: List<DocumentLine>) {
    totalExclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
    totalVat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
    totalInclTax = totalExclTax.add(totalVat)
  }

  enum class QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REFUSED,
    EXPIRED,
  }
}
