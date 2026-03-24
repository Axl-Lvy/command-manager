package fr.axl.lvy.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(nullable = false, unique = true, length = 50)
  private String reference;

  @NotBlank
  @Column(nullable = false)
  private String designation;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String description;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProductType type = ProductType.PRODUCT;

  @Column(name = "is_mto", nullable = false)
  private boolean mto = false;

  @NotNull
  @Column(name = "selling_price_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal sellingPriceExclTax = BigDecimal.ZERO;

  @NotNull
  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  private BigDecimal purchasePriceExclTax = BigDecimal.ZERO;

  @NotNull
  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal vatRate = BigDecimal.ZERO;

  @Nullable
  @Column(length = 20)
  private String unit;

  @Nullable
  @Column(name = "hs_code", length = 20)
  private String hsCode;

  @Nullable
  @Column(name = "made_in", length = 100)
  private String madeIn;

  @Nullable
  @Column(name = "client_product_code", length = 100)
  private String clientProductCode;

  private boolean active = true;

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

  public Product(String reference, String designation) {
    this.reference = reference;
    this.designation = designation;
  }

  @PrePersist
  void prePersist() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
    if (type == ProductType.SERVICE) mto = false;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
    if (type == ProductType.SERVICE) mto = false;
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
    Product other = (Product) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum ProductType {
    PRODUCT,
    SERVICE
  }
}
