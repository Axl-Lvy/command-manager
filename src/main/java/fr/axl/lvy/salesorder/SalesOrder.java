package fr.axl.lvy.salesorder;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.delivery.SalesDeliveryNote;
import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.invoice.SalesInvoice;
import fr.axl.lvy.purchaseorder.PurchaseOrder;
import fr.axl.lvy.quote.Quote;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "sales_orders")
@Getter
@Setter
@NoArgsConstructor
public class SalesOrder {

  private static final Set<SalesOrderStatus> EDITABLE =
      Set.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.IN_PRODUCTION, SalesOrderStatus.READY);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  private String orderNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quote_id")
  private Quote quote;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_order_id")
  private SalesOrder sourceOrder;

  @Nullable
  @Column(name = "client_reference", length = 100)
  private String clientReference;

  @Nullable private String subject;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SalesOrderStatus status = SalesOrderStatus.CONFIRMED;

  @NotNull
  @Column(name = "order_date", nullable = false)
  private LocalDate orderDate;

  @Nullable
  @Column(name = "expected_delivery_date")
  private LocalDate expectedDeliveryDate;

  @Nullable
  @Column(name = "billing_address", columnDefinition = "TEXT")
  private String billingAddress;

  @Nullable
  @Column(name = "shipping_address", columnDefinition = "TEXT")
  private String shippingAddress;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalExclTax = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalVat = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "total_incl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalInclTax = BigDecimal.ZERO;

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal purchasePriceExclTax = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "margin_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal marginExclTax = BigDecimal.ZERO;

  @Column(nullable = false, length = 5)
  private String currency = "EUR";

  @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
  private BigDecimal exchangeRate = BigDecimal.ONE;

  @Nullable
  @Column(length = 10)
  private String incoterms;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String notes;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String conditions;

  @Nullable
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "purchase_order_id")
  private PurchaseOrder purchaseOrder;

  @Nullable
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  private SalesDeliveryNote deliveryNote;

  @Nullable
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id")
  private SalesInvoice invoice;

  @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<DocumentLine> lines = new ArrayList<>();

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

  public SalesOrder(String orderNumber, Client client, LocalDate orderDate) {
    this.orderNumber = orderNumber;
    this.client = client;
    this.orderDate = orderDate;
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

  public boolean isEditable() {
    return EDITABLE.contains(status);
  }

  public void recalculateTotals() {
    totalExclTax =
        lines.stream()
            .map(DocumentLine::getLineTotalExclTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    totalVat =
        lines.stream().map(DocumentLine::getVatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    totalInclTax = totalExclTax.add(totalVat);
    marginExclTax = totalExclTax.subtract(purchasePriceExclTax);
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
    SalesOrder other = (SalesOrder) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum SalesOrderStatus {
    CONFIRMED,
    IN_PRODUCTION,
    READY,
    DELIVERED,
    INVOICED,
    CANCELLED
  }
}
