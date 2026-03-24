package fr.axl.lvy.documentline;

import fr.axl.lvy.order.OrderA;
import fr.axl.lvy.order.OrderB;
import fr.axl.lvy.product.Product;
import fr.axl.lvy.quote.Quote;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "document_lines",
    indexes = {
      @Index(name = "idx_document_lines_type_id", columnList = "document_type, document_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class DocumentLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private Quote quote;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private OrderA orderA;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private OrderB orderB;

  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  @NotBlank
  @Column(nullable = false)
  private String designation;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String description;

  @Nullable
  @Column(name = "hs_code", length = 20)
  private String hsCode;

  @Nullable
  @Column(name = "made_in", length = 100)
  private String madeIn;

  @Nullable
  @Column(name = "client_product_code", length = 100)
  private String clientProductCode;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal quantity = BigDecimal.ONE;

  @Nullable
  @Column(length = 20)
  private String unit;

  @Column(name = "unit_price_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPriceExclTax = BigDecimal.ZERO;

  @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal discountPercent = BigDecimal.ZERO;

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal vatRate = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal vatAmount = BigDecimal.ZERO;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "line_total_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal lineTotalExclTax = BigDecimal.ZERO;

  private int position = 0;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public DocumentLine(DocumentType documentType, Long documentId, String designation) {
    this.documentType = documentType;
    this.documentId = documentId;
    this.designation = designation;
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

  public void recalculate() {
    BigDecimal discountMultiplier =
        BigDecimal.ONE.subtract(
            discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    lineTotalExclTax =
        quantity
            .multiply(unitPriceExclTax)
            .multiply(discountMultiplier)
            .setScale(2, RoundingMode.HALF_UP);
    vatAmount =
        lineTotalExclTax.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  public static DocumentLine fromProduct(
      DocumentType documentType, Long documentId, Product product) {
    DocumentLine line = new DocumentLine(documentType, documentId, product.getDesignation());
    line.setProduct(product);
    line.setHsCode(product.getHsCode());
    line.setMadeIn(product.getMadeIn());
    line.setClientProductCode(product.getClientProductCode());
    line.setUnit(product.getUnit());
    line.setUnitPriceExclTax(product.getSellingPriceExclTax());
    line.setQuantity(BigDecimal.ONE);
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(BigDecimal.ZERO);
    line.recalculate();
    return line;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !getClass().isAssignableFrom(obj.getClass())) return false;
    DocumentLine other = (DocumentLine) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum DocumentType {
    QUOTE,
    ORDER_A,
    ORDER_B,
    INVOICE_A,
    INVOICE_B
  }
}
