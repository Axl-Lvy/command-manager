package fr.axl.lvy.invoice

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.delivery.DeliveryNoteA
import fr.axl.lvy.order.OrderA
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "invoices_a")
class InvoiceA(
  @NotBlank
  @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
  var invoiceNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @Column(name = "invoice_date", nullable = false) var invoiceDate: LocalDate,
) : SoftDeletableEntity() {
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_a_id") var orderA: OrderA? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  var deliveryNote: DeliveryNoteA? = null

  @Column(name = "client_name") var clientName: String? = null

  @Column(name = "client_address", columnDefinition = "TEXT") var clientAddress: String? = null

  @Column(name = "client_siret", length = 20) var clientSiret: String? = null

  @Column(name = "client_vat_number", length = 20) var clientVatNumber: String? = null

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: InvoiceAStatus = InvoiceAStatus.DRAFT

  @Column(name = "due_date") var dueDate: LocalDate? = null

  @Column(name = "payment_date") var paymentDate: LocalDate? = null

  @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
  var amountPaid: BigDecimal = BigDecimal.ZERO

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

  @Column(length = 10) var incoterms: String? = null

  @Column(name = "legal_notice", columnDefinition = "TEXT") var legalNotice: String? = null

  @Column(name = "late_penalties", columnDefinition = "TEXT") var latePenalties: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credit_note_id")
  var creditNote: InvoiceA? = null

  enum class InvoiceAStatus {
    DRAFT,
    ISSUED,
    OVERDUE,
    PAID,
    CANCELLED,
    CREDIT_NOTE,
  }
}
