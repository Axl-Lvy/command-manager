package fr.axl.lvy.documentline

import fr.axl.lvy.product.Product
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Entity
@Table(
  name = "document_lines",
  indexes = [Index(name = "idx_document_lines_type_id", columnList = "document_type, document_id")],
)
class DocumentLine(
  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  var documentType: DocumentType,
  @Column(name = "document_id", nullable = false) var documentId: Long,
  @NotBlank @Column(nullable = false) var designation: String,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") var product: Product? = null

  @Column(columnDefinition = "TEXT") var description: String? = null

  @Column(name = "hs_code", length = 20) var hsCode: String? = null

  @Column(name = "made_in", length = 100) var madeIn: String? = null

  @Column(name = "client_product_code", length = 100) var clientProductCode: String? = null

  @Column(nullable = false, precision = 10, scale = 2) var quantity: BigDecimal = BigDecimal.ONE

  @Column(length = 20) var unit: String? = null

  @Column(name = "unit_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var unitPriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
  var discountPercent: BigDecimal = BigDecimal.ZERO

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  var vatRate: BigDecimal = BigDecimal.ZERO

  @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
  var vatAmount: BigDecimal = BigDecimal.ZERO
    private set

  @Column(name = "line_total_excl_tax", nullable = false, precision = 12, scale = 2)
  var lineTotalExclTax: BigDecimal = BigDecimal.ZERO
    private set

  var position: Int = 0

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
  }

  fun recalculate() {
    val discountMultiplier =
      BigDecimal.ONE.subtract(
        discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
      )
    lineTotalExclTax =
      quantity
        .multiply(unitPriceExclTax)
        .multiply(discountMultiplier)
        .setScale(2, RoundingMode.HALF_UP)
    vatAmount =
      lineTotalExclTax.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as DocumentLine
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  companion object {
    fun fromProduct(documentType: DocumentType, documentId: Long, product: Product): DocumentLine {
      val line = DocumentLine(documentType, documentId, product.designation)
      line.product = product
      line.hsCode = product.hsCode
      line.madeIn = product.madeIn
      line.clientProductCode = product.clientProductCode
      line.unit = product.unit
      line.unitPriceExclTax = product.sellingPriceExclTax
      line.quantity = BigDecimal.ONE
      line.discountPercent = BigDecimal.ZERO
      line.vatRate = BigDecimal.ZERO
      line.recalculate()
      return line
    }
  }

  enum class DocumentType {
    QUOTE,
    ORDER_A,
    ORDER_B,
    INVOICE_A,
    INVOICE_B,
  }
}
