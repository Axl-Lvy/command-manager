package fr.axl.lvy.order

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.invoice.InvoiceB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
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
) : SoftDeletableEntity() {
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

  fun recalculateTotals(lines: List<DocumentLine>) {
    totalExclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
    totalVat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
    totalInclTax = totalExclTax.add(totalVat)
  }

  enum class OrderBStatus {
    SENT,
    CONFIRMED,
    IN_PRODUCTION,
    RECEIVED,
    CANCELLED,
  }
}
