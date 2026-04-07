package fr.axl.lvy.documentline

import fr.axl.lvy.base.BaseEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.product.Product
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.math.RoundingMode

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
) : BaseEntity() {
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

  fun copyFieldsFrom(
    source: DocumentLine,
    overrideVatRate: BigDecimal? = null,
    overrideUnitPrice: BigDecimal? = null,
  ) {
    designation = source.designation
    product = source.product
    description = source.description
    hsCode = source.hsCode
    madeIn = source.madeIn
    clientProductCode = source.clientProductCode
    quantity = source.quantity
    unit = source.unit
    unitPriceExclTax = overrideUnitPrice ?: source.unitPriceExclTax
    discountPercent = source.discountPercent
    vatRate = overrideVatRate ?: source.vatRate
    recalculate()
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

  companion object {
    fun fromProduct(
      documentType: DocumentType,
      documentId: Long,
      product: Product,
      client: Client? = null,
    ): DocumentLine {
      val line = DocumentLine(documentType, documentId, product.name)
      line.product = product
      line.hsCode = product.hsCode
      line.madeIn = product.madeIn
      line.clientProductCode = product.findClientProductCode(client)
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
    SALES_A,
    SALES_B,
    ORDER_A,
    ORDER_B,
    INVOICE_A,
    INVOICE_B,
  }
}
