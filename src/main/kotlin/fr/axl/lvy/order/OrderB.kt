package fr.axl.lvy.order

import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.invoice.InvoiceB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "orders_b")
class OrderB(
  @NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_a_id", nullable = false)
  var orderA: OrderA,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: OrderBStatus = OrderBStatus.SENT

  @Column(name = "order_date") var orderDate: LocalDate? = null

  @Column(name = "expected_delivery_date") var expectedDeliveryDate: LocalDate? = null

  @Column(name = "reception_date") var receptionDate: LocalDate? = null

  @Column(name = "reception_conforming") var receptionConforming: Boolean? = null

  @Column(name = "reception_reserve", columnDefinition = "TEXT")
  var receptionReserve: String? = null

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

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_b_id")
  var invoiceB: InvoiceB? = null

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

  fun recalculateTotals(lines: List<DocumentLine>) {
    totalExclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
    totalVat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
    totalInclTax = totalExclTax.add(totalVat)
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
    other as OrderB
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class OrderBStatus {
    SENT,
    CONFIRMED,
    IN_PRODUCTION,
    RECEIVED,
    CANCELLED,
  }
}
