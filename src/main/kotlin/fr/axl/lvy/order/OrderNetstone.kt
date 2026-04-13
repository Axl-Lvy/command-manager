package fr.axl.lvy.order

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.client.Client
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.invoice.InvoiceNetstone
import fr.axl.lvy.paymentterm.PaymentTerm
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A supplier purchase order placed by Netstone, always linked to a parent [OrderCodig]. Follows a
 * status workflow: SENT -> CONFIRMED -> RECEIVED (or CANCELLED).
 *
 * Tracks reception details (date, conformity, reserves) when goods arrive.
 */
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
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id") var supplier: Client? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('SENT','CONFIRMED','RECEIVED','CANCELLED')")
  var status: OrderNetstoneStatus = OrderNetstoneStatus.SENT

  @Column(name = "order_date") var orderDate: LocalDate? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_term_id")
  var paymentTerm: PaymentTerm? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiscal_position_id")
  var fiscalPosition: FiscalPosition? = null

  @Column(name = "delivery_location", columnDefinition = "TEXT")
  var deliveryLocation: String? = null

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
    RECEIVED,
    CANCELLED,
  }
}
