package fr.axl.lvy.product

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(name = "products")
class Product(
  @NotBlank @Column(nullable = false, unique = true, length = 50) var reference: String = "",
  @NotBlank @Column(name = "designation", nullable = false) var name: String,
) : SoftDeletableEntity() {
  @Column(name = "description", columnDefinition = "TEXT") var specifications: String? = null

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

  var active: Boolean = true

  @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
  var clientProductCodes: MutableList<ProductClientCode> = mutableListOf()

  fun replaceClientProductCodes(entries: List<Pair<Client, String>>) {
    clientProductCodes.clear()
    entries.forEach { (client, code) ->
      val clientProductCode = ProductClientCode(this, client, code)
      clientProductCodes.add(clientProductCode)
    }
  }

  fun findClientProductCode(client: Client?): String? =
    client?.let { currentClient ->
      clientProductCodes.firstOrNull { it.client == currentClient }?.code
    }

  @PrePersist
  fun validateOnPersist() {
    if (type == ProductType.SERVICE) mto = false
  }

  @PreUpdate
  fun validateOnUpdate() {
    if (type == ProductType.SERVICE) mto = false
  }

  enum class ProductType {
    PRODUCT,
    SERVICE,
  }
}
