package fr.axl.lvy.invoice

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.user.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "invoices_netstone")
class InvoiceNetstone(
  @NotBlank
  @Column(name = "internal_invoice_number", nullable = false, unique = true, length = 20)
  var internalInvoiceNumber: String,
  @Enumerated(EnumType.STRING)
  @Column(name = "recipient_type", nullable = false)
  var recipientType: RecipientType,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  var recipient: Client,
  @Column(name = "invoice_date", nullable = false) var invoiceDate: LocalDate,
) : SoftDeletableEntity() {
  @Column(name = "supplier_invoice_number", length = 50) var supplierInvoiceNumber: String? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_netstone_id")
  var orderNetstone: OrderNetstone? = null

  @Enumerated(EnumType.STRING) @Column(nullable = false) var origin: Origin = Origin.ORDER_LINKED

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: InvoiceNetstoneStatus = InvoiceNetstoneStatus.RECEIVED

  @Column(name = "due_date") var dueDate: LocalDate? = null

  @Column(name = "verification_date") var verificationDate: LocalDate? = null

  @Column(name = "payment_date") var paymentDate: LocalDate? = null

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "verified_by") var verifiedBy: User? = null

  @Column(name = "dispute_reason", columnDefinition = "TEXT") var disputeReason: String? = null

  @Column(name = "amount_discrepancy", precision = 12, scale = 2)
  var amountDiscrepancy: BigDecimal? = null

  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  var totalExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  var totalVat: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  var totalInclTax: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "pdf_path", length = 500) var pdfPath: String? = null

  @Column(columnDefinition = "TEXT") var notes: String? = null

  enum class RecipientType {
    COMPANY_CODIG,
    PRODUCER,
  }

  enum class Origin {
    ORDER_LINKED,
    STANDALONE,
  }

  enum class InvoiceNetstoneStatus {
    RECEIVED,
    VERIFIED,
    PAID,
    DISPUTED,
  }
}
