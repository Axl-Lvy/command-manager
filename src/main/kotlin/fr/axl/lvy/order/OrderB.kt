package fr.axl.lvy.order

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.invoice.InvoiceB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Entity
@Table(name = "orders_b")
class OrderB(
  @field:NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_a_id", nullable = false)
  var orderA: OrderA,
) : TotalizableDocument() {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: OrderBStatus = OrderBStatus.SENT

  @Column(name = "order_date") var orderDate: LocalDate? = null

  @Column(name = "reception_date") var receptionDate: LocalDate? = null

  @Column(name = "reception_conforming") var receptionConforming: Boolean? = null

  @Column(name = "reception_reserve", columnDefinition = "TEXT")
  var receptionReserve: String? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_b_id")
  var invoiceB: InvoiceB? = null

  enum class OrderBStatus {
    SENT,
    CONFIRMED,
    IN_PRODUCTION,
    RECEIVED,
    CANCELLED,
  }
}
