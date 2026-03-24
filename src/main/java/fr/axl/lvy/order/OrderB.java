package fr.axl.lvy.order;

import fr.axl.lvy.documentline.DocumentLine;
import fr.axl.lvy.invoice.InvoiceB;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "orders_b")
@Getter
@Setter
@NoArgsConstructor
public class OrderB {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  private String orderNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_a_id", nullable = false)
  private OrderA orderA;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderBStatus status = OrderBStatus.SENT;

  @Nullable
  @Column(name = "order_date")
  private LocalDate orderDate;

  @Nullable
  @Column(name = "expected_delivery_date")
  private LocalDate expectedDeliveryDate;

  @Nullable
  @Column(name = "reception_date")
  private LocalDate receptionDate;

  @Nullable
  @Column(name = "reception_conforming")
  private Boolean receptionConforming;

  @Nullable
  @Column(name = "reception_reserve", columnDefinition = "TEXT")
  private String receptionReserve;

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
  @Column(columnDefinition = "TEXT")
  private String notes;

  @Nullable
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_b_id")
  private InvoiceB invoiceB;

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

  public OrderB(String orderNumber, OrderA orderA) {
    this.orderNumber = orderNumber;
    this.orderA = orderA;
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

  public void recalculateTotals(List<DocumentLine> lines) {
    totalExclTax =
        lines.stream()
            .map(DocumentLine::getLineTotalExclTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    totalVat =
        lines.stream().map(DocumentLine::getVatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    totalInclTax = totalExclTax.add(totalVat);
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
    OrderB other = (OrderB) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum OrderBStatus {
    SENT,
    CONFIRMED,
    IN_PRODUCTION,
    RECEIVED,
    CANCELLED
  }
}
