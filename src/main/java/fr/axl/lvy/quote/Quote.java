package fr.axl.lvy.quote;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.documentline.DocumentLine;
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
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class Quote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "quote_number", nullable = false, unique = true, length = 20)
  private String quoteNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Nullable
  @Column(name = "client_reference", length = 100)
  private String clientReference;

  @Nullable private String subject;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private QuoteStatus status = QuoteStatus.DRAFT;

  @NotNull
  @Column(name = "quote_date", nullable = false)
  private LocalDate quoteDate;

  @Nullable
  @Column(name = "validity_date")
  private LocalDate validityDate;

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

  public Quote(String quoteNumber, Client client, LocalDate quoteDate) {
    this.quoteNumber = quoteNumber;
    this.client = client;
    this.quoteDate = quoteDate;
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
    return status == QuoteStatus.DRAFT || status == QuoteStatus.SENT;
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
    Quote other = (Quote) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REFUSED,
    EXPIRED
  }
}
