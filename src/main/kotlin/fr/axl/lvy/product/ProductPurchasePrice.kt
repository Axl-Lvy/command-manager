package fr.axl.lvy.product

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** Purchase price of a product for one internal company. */
@Entity
@Table(
  name = "product_purchase_prices",
  uniqueConstraints =
    [
      UniqueConstraint(
        name = "uk_product_purchase_price_company",
        columnNames = ["product_id", "company"],
      )
    ],
)
class ProductPurchasePrice(
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  var product: Product,
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('CODIG','NETSTONE')")
  var company: ProductPriceCompany,
  @field:NotNull
  @Column(name = "price_excl_tax", nullable = false, precision = 12, scale = 2)
  var priceExclTax: BigDecimal,
  @Column(nullable = false, length = 3) var currency: String,
) : BaseEntity()
