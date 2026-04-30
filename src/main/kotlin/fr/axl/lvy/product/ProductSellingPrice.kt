package fr.axl.lvy.product

import fr.axl.lvy.base.BaseEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/** Customer-specific selling price used by CoDIG sales documents. */
@Entity
@Table(
  name = "product_selling_prices",
  uniqueConstraints =
    [
      UniqueConstraint(
        name = "uk_product_selling_price_client",
        columnNames = ["product_id", "client_id"],
      )
    ],
)
class ProductSellingPrice(
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  var product: Product,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @field:NotNull
  @Column(name = "price_excl_tax", nullable = false, precision = 12, scale = 2)
  var priceExclTax: BigDecimal,
  @Column(nullable = false, length = 3) var currency: String,
) : BaseEntity()
