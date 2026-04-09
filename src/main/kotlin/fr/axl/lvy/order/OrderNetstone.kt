package fr.axl.lvy.order

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.invoice.InvoiceNetstone
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Entity
@Table(name = "orders_netstone")
class OrderNetstone(
  @field:NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_codig_id", nullable = false)
  var orderCodig: OrderCodig,
) : TotalizableDocument() {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: OrderNetstoneStatus = OrderNetstoneStatus.SENT

  @Column(name = "order_date") var orderDate: LocalDate? = null

  @Column(name = "reception_date") var receptionDate: LocalDate? = null

  @Column(name = "reception_conforming") var receptionConforming: Boolean? = null

  @Column(name = "reception_reserve", columnDefinition = "TEXT")
  var receptionReserve: String? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_netstone_id")
  var invoiceNetstone: InvoiceNetstone? = null

  enum class OrderNetstoneStatus {
    SENT,
    CONFIRMED,
    IN_PRODUCTION,
    RECEIVED,
    CANCELLED,
  }
}
