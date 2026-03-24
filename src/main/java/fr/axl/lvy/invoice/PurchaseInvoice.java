package fr.axl.lvy.invoice;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.purchaseorder.PurchaseOrder;
import fr.axl.lvy.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "purchase_invoices")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @Nullable
  @Column(name = "supplier_invoice_number", length = 50)
  private String supplierInvoiceNumber;

  @NotBlank
  @Column(name = "internal_invoice_number", nullable = false, unique = true, length = 20)
  private String internalInvoiceNumber;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "purchase_order_id")
  private PurchaseOrder purchaseOrder;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "recipient_type", nullable = false)
  private RecipientType recipientType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private Client recipient;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Origin origin = Origin.ORDER_LINKED;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PurchaseInvoiceStatus status = PurchaseInvoiceStatus.RECEIVED;

  @NotNull
  @Column(name = "invoice_date", nullable = false)
  private LocalDate invoiceDate;

  @Nullable
  @Column(name = "due_date")
  private LocalDate dueDate;

  @Nullable
  @Column(name = "verification_date")
  private LocalDate verificationDate;

  @Nullable
  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "verified_by")
  private User verifiedBy;

  @Nullable
  @Column(name = "dispute_reason", columnDefinition = "TEXT")
  private String disputeReason;

  @Nullable
  @Column(name = "amount_discrepancy", precision = 12, scale = 2)
  private BigDecimal amountDiscrepancy;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalExclTax = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalVat = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalInclTax = BigDecimal.ZERO;

  @Nullable
  @Column(name = "pdf_path", length = 500)
  private String pdfPath;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String notes;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Nullable
  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "deleted_at")
  private Instant deletedAt;

  public PurchaseInvoice(
      String internalInvoiceNumber,
      RecipientType recipientType,
      Client recipient,
      LocalDate invoiceDate) {
    this.internalInvoiceNumber = internalInvoiceNumber;
    this.recipientType = recipientType;
    this.recipient = recipient;
    this.invoiceDate = invoiceDate;
  }

  @PrePersist
  void prePersist() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deletedAt = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !getClass().isAssignableFrom(obj.getClass())) return false;
    PurchaseInvoice other = (PurchaseInvoice) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum RecipientType {
    COMPANY_A,
    PRODUCER
  }

  public enum Origin {
    ORDER_LINKED,
    STANDALONE
  }

  public enum PurchaseInvoiceStatus {
    RECEIVED,
    VERIFIED,
    PAID,
    DISPUTED
  }
}
