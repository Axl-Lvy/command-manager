package fr.axl.lvy.order

import fr.axl.lvy.client.Client
import fr.axl.lvy.delivery.DeliveryNoteA
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.invoice.InvoiceA
import fr.axl.lvy.quote.Quote
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "orders_a")
class OrderA(
  @NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @Column(name = "order_date", nullable = false) var orderDate: LocalDate,
) {
  companion object {
    private val EDITABLE =
      setOf(OrderAStatus.CONFIRMED, OrderAStatus.IN_PRODUCTION, OrderAStatus.READY)
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quote_id") var quote: Quote? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_order_id")
  var sourceOrder: OrderA? = null

  @Column(name = "client_reference", length = 100) var clientReference: String? = null

  var subject: String? = null

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: OrderAStatus = OrderAStatus.CONFIRMED

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

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "margin_excl_tax", nullable = false, precision = 12, scale = 2)
  var marginExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @Column(length = 10) var incoterms: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @Column(columnDefinition = "TEXT") var conditions: String? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_b_id") var orderB: OrderB? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  var deliveryNote: DeliveryNoteA? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") var invoice: InvoiceA? = null

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  @Column(name = "deleted_at")
  var deletedAt: Instant? = null
    private set

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
  }

  fun isEditable(): Boolean = EDITABLE.contains(status)

  fun recalculateTotals(lines: List<DocumentLine>) {
    totalExclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
    totalVat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
    totalInclTax = totalExclTax.add(totalVat)
    marginExclTax = totalExclTax.subtract(purchasePriceExclTax)
  }

  fun isDeleted(): Boolean = deletedAt != null

  fun softDelete() {
    deletedAt = Instant.now()
  }

  fun restore() {
    deletedAt = null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as OrderA
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class OrderAStatus {
    CONFIRMED,
    IN_PRODUCTION,
    READY,
    DELIVERED,
    INVOICED,
    CANCELLED,
  }
}
