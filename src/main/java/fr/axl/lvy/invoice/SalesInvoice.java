package fr.axl.lvy.invoice;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.delivery.SalesDeliveryNote;
import fr.axl.lvy.salesorder.SalesOrder;
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
@Table(name = "sales_invoices")
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
  private String invoiceNumber;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sales_order_id")
  private SalesOrder salesOrder;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  private SalesDeliveryNote deliveryNote;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Nullable
  @Column(name = "client_name")
  private String clientName;

  @Nullable
  @Column(name = "client_address", columnDefinition = "TEXT")
  private String clientAddress;

  @Nullable
  @Column(name = "client_siret", length = 20)
  private String clientSiret;

  @Nullable
  @Column(name = "client_vat_number", length = 20)
  private String clientVatNumber;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SalesInvoiceStatus status = SalesInvoiceStatus.DRAFT;

  @NotNull
  @Column(name = "invoice_date", nullable = false)
  private LocalDate invoiceDate;

  @Nullable
  @Column(name = "due_date")
  private LocalDate dueDate;

  @Nullable
  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
  private BigDecimal amountPaid = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalExclTax = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalVat = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalInclTax = BigDecimal.ZERO;

  @Column(nullable = false, length = 5)
  private String currency = "EUR";

  @Nullable
  @Column(length = 10)
  private String incoterms;

  @Nullable
  @Column(name = "legal_notice", columnDefinition = "TEXT")
  private String legalNotice;

  @Nullable
  @Column(name = "late_penalties", columnDefinition = "TEXT")
  private String latePenalties;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String notes;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credit_note_id")
  private SalesInvoice creditNote;

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

  public SalesInvoice(String invoiceNumber, Client client, LocalDate invoiceDate) {
    this.invoiceNumber = invoiceNumber;
    this.client = client;
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
    SalesInvoice other = (SalesInvoice) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum SalesInvoiceStatus {
    DRAFT,
    ISSUED,
    OVERDUE,
    PAID,
    CANCELLED,
    CREDIT_NOTE
  }
}
