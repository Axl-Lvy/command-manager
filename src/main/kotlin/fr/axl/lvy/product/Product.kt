package fr.axl.lvy.product

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "products")
class Product(
  @NotBlank @Column(nullable = false, unique = true, length = 50) var reference: String,
  @NotBlank @Column(nullable = false) var designation: String,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Column(columnDefinition = "TEXT") var description: String? = null

  @Enumerated(EnumType.STRING) @Column(nullable = false) var type: ProductType = ProductType.PRODUCT

  @Column(name = "is_mto", nullable = false) var mto: Boolean = false

  @Column(name = "selling_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var sellingPriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  var vatRate: BigDecimal = BigDecimal.ZERO

  @Column(length = 20) var unit: String? = null

  @Column(name = "hs_code", length = 20) var hsCode: String? = null

  @Column(name = "made_in", length = 100) var madeIn: String? = null

  @Column(name = "client_product_code", length = 100) var clientProductCode: String? = null

  var active: Boolean = true

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  @Column(name = "deleted_at")
  var deletedAt: Instant? = null
    private set

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
    if (type == ProductType.SERVICE) mto = false
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
    if (type == ProductType.SERVICE) mto = false
  }

  fun isDeleted(): Boolean = deletedAt != null

  fun softDelete() {
    deletedAt = Instant.now()
  }

  fun restore() {
    deletedAt = null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as Product
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class ProductType {
    PRODUCT,
    SERVICE,
  }
}
