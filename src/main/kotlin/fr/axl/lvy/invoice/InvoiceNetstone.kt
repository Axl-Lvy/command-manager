package fr.axl.lvy.invoice

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.user.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A supplier invoice received and processed by Netstone. Can be linked to an [OrderNetstone] or
 * standalone. The [recipientType] distinguishes invoices billed to Codig vs. to an external
 * producer.
 *
 * Status workflow: RECEIVED -> VERIFIED -> PAID (or DISPUTED).
 */
@Entity
@Table(name = "invoices_netstone")
class InvoiceNetstone(
  @NotBlank
  @Column(name = "internal_invoice_number", nullable = false, unique = true, length = 20)
  var internalInvoiceNumber: String,
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(
    name = "recipient_type",
    nullable = false,
    columnDefinition = "enum('COMPANY_CODIG','PRODUCER')",
  )
  var recipientType: RecipientType,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  var recipient: Client,
  @Column(name = "invoice_date", nullable = false) var invoiceDate: LocalDate,
) : SoftDeletableEntity() {
  @Column(name = "billing_address", columnDefinition = "TEXT") var billingAddress: String? = null

  @Column(name = "supplier_invoice_number", length = 50) var supplierInvoiceNumber: String? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_netstone_id")
  var orderNetstone: OrderNetstone? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('ORDER_LINKED','STANDALONE')")
  var origin: Origin = Origin.ORDER_LINKED

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('RECEIVED','VERIFIED','PAID','DISPUTED')")
  var status: InvoiceNetstoneStatus = InvoiceNetstoneStatus.RECEIVED

  @Column(name = "due_date") var dueDate: LocalDate? = null

  @Column(name = "verification_date") var verificationDate: LocalDate? = null

  @Column(name = "payment_date") var paymentDate: LocalDate? = null

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "verified_by") var verifiedBy: User? = null

  @Column(name = "dispute_reason", columnDefinition = "TEXT") var disputeReason: String? = null

  /** Difference between the invoiced amount and the expected amount from the linked order. */
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

  /** Recomputes invoice totals from the current set of persisted invoice lines. */
  fun recalculateTotals(lines: List<DocumentLine>) {
    val totals = DocumentLine.computeTotals(lines)
    totalExclTax = totals.exclTax
    totalVat = totals.vat
    totalInclTax = totals.inclTax
  }

  enum class RecipientType {
    /** Invoice billed to Codig (inter-company). */
    COMPANY_CODIG,

    /** Invoice billed to an external producer/supplier. */
    PRODUCER,
  }

  enum class Origin {
    /** Created from an existing Netstone order. */
    ORDER_LINKED,

    /** Manually created without a linked order. */
    STANDALONE,
  }

  enum class InvoiceNetstoneStatus {
    RECEIVED,
    VERIFIED,
    PAID,
    DISPUTED,
  }
}
