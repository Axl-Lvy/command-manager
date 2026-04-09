package fr.axl.lvy.order

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.delivery.DeliveryNoteA
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.invoice.InvoiceA
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "orders_a")
class OrderA(
  @field:NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @Column(name = "order_date", nullable = false) var orderDate: LocalDate,
) : SoftDeletableEntity() {
  companion object {
    private val EDITABLE =
      setOf(
        OrderAStatus.DRAFT,
        OrderAStatus.CONFIRMED,
        OrderAStatus.IN_PRODUCTION,
        OrderAStatus.READY,
      )
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_order_id")
  var sourceOrder: OrderA? = null

  @Column(name = "client_reference", length = 100) var clientReference: String? = null

  var subject: String? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
  var status: OrderAStatus = OrderAStatus.DRAFT

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

  @Column(name = "margin_excl_tax", nullable = false, precision = 12, scale = 2)
  var marginExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(length = 10) var incoterms: String? = null

  @Column(name = "incoterm_location", length = 100) var incotermLocation: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @Column(columnDefinition = "TEXT") var conditions: String? = null

  @OneToOne(mappedBy = "orderA", fetch = FetchType.LAZY) var orderB: OrderB? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  var deliveryNote: DeliveryNoteA? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") var invoice: InvoiceA? = null

  fun isEditable(): Boolean = EDITABLE.contains(status)

  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
    marginExclTax = BigDecimal.ZERO
  }

  enum class OrderAStatus {
    DRAFT,
    CONFIRMED,
    IN_PRODUCTION,
    READY,
    DELIVERED,
    INVOICED,
    CANCELLED,
  }
}
