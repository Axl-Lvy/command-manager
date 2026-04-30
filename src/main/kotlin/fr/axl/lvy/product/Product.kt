package fr.axl.lvy.product

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A sellable item in the product catalog. Can be a physical [ProductType.PRODUCT] or a
 * [ProductType.SERVICE]. Physical products may be flagged as [mto] (made-to-order), meaning they
 * trigger a supplier purchase order when sold.
 *
 * Pricing is stored outside the product sheet: [purchasePrices] for CoDIG/Netstone purchase costs
 * and [sellingPrices] for CoDIG client-specific selling prices. The product itself keeps only
 * stable catalog data plus per-client reference codes via [clientProductCodes].
 */
@Entity
@Table(name = "products")
class Product(
  @NotBlank @Column(nullable = false, unique = true, length = 50) var reference: String = "",
  @NotBlank @Column(name = "designation", nullable = false) var name: String,
) : SoftDeletableEntity() {
  /** Alternate commercial wording used on documents when the product name is too technical. */
  @Column(name = "label", length = 255) var label: String? = null

  /** Short customer-facing summary shown in compact contexts. */
  @Column(name = "short_description", length = 500) var shortDescription: String? = null

  /** Long-form commercial or technical description kept on the product sheet. */
  @Column(name = "long_description", columnDefinition = "TEXT") var longDescription: String? = null

  /** Technical specifications reused by current document PDFs and detailed product content. */
  @Column(name = "description", columnDefinition = "TEXT") var specifications: String? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('PRODUCT','SERVICE')")
  var type: ProductType = ProductType.PRODUCT

  @Column(name = "is_mto", nullable = false) var mto: Boolean = false

  @Column(name = "selling_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var sellingPriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "selling_currency", nullable = false, length = 3)
  var sellingCurrency: String = "EUR"

  @Column(name = "purchase_price_excl_tax", nullable = false, precision = 12, scale = 2)
  var purchasePriceExclTax: BigDecimal = BigDecimal.ZERO

  @Column(name = "purchase_currency", nullable = false, length = 3)
  var purchaseCurrency: String = "EUR"

  @Column(length = 20) var unit: String? = null

  @Column(name = "hs_code", length = 20) var hsCode: String? = null

  /** Chemical Abstracts Service identifier used for regulated chemical products. */
  @Column(name = "cas_number", length = 50) var casNumber: String? = null

  /** European Community identifier paired with the CAS number on product sheets. */
  @Column(name = "ec_number", length = 50) var ecNumber: String? = null

  @Column(name = "made_in", length = 100) var madeIn: String? = null

  var active: Boolean = true

  @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
  var clientProductCodes: MutableList<ProductClientCode> = mutableListOf()

  @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
  var purchasePrices: MutableList<ProductPurchasePrice> = mutableListOf()

  @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
  var sellingPrices: MutableList<ProductSellingPrice> = mutableListOf()

  @ManyToMany
  @JoinTable(
    name = "product_suppliers",
    joinColumns = [JoinColumn(name = "product_id")],
    inverseJoinColumns = [JoinColumn(name = "client_id")],
  )
  var suppliers: MutableSet<Client> = linkedSetOf()

  /** Replaces all client-specific product codes with the given entries. */
  fun replaceClientProductCodes(entries: List<Pair<Client, String>>) {
    val validEntries = entries.filter { (_, code) -> code.isNotBlank() }
    val entriesByClientId =
      validEntries
        .filter { (client, _) -> client.id != null }
        .associateBy({ (client, _) -> client.id!! }, { it })
    val transientEntries = validEntries.filter { (client, _) -> client.id == null }

    val iterator = clientProductCodes.iterator()
    while (iterator.hasNext()) {
      val current = iterator.next()
      val desired = current.client.id?.let(entriesByClientId::get)
      if (desired == null) {
        iterator.remove()
      } else {
        current.client = desired.first
        current.code = desired.second
      }
    }

    val existingClientIds = clientProductCodes.mapNotNull { it.client.id }.toSet()
    entriesByClientId.forEach { (clientId, entry) ->
      if (clientId !in existingClientIds) {
        clientProductCodes.add(ProductClientCode(this, entry.first, entry.second))
      }
    }
    transientEntries.forEach { (client, code) ->
      clientProductCodes.add(ProductClientCode(this, client, code))
    }
  }

  /** Returns the custom reference code that [client] uses for this product, if any. */
  fun findClientProductCode(client: Client?): String? =
    client?.let { currentClient ->
      clientProductCodes.firstOrNull { it.client == currentClient }?.code
    }

  /** Replaces the internal-company purchase prices kept on the product. */
  fun replacePurchasePrices(entries: List<PurchasePriceEntry>) {
    val byCompany = entries.associateBy { it.company }

    val iterator = purchasePrices.iterator()
    while (iterator.hasNext()) {
      val current = iterator.next()
      val desired = byCompany[current.company]
      if (desired == null) {
        iterator.remove()
      } else {
        current.priceExclTax = desired.priceExclTax
        current.currency = desired.currency
      }
    }

    val existingCompanies = purchasePrices.map { it.company }.toSet()
    byCompany.forEach { (company, entry) ->
      if (company !in existingCompanies) {
        purchasePrices.add(ProductPurchasePrice(this, company, entry.priceExclTax, entry.currency))
      }
    }
  }

  /** Replaces the client-specific selling prices kept on the product. */
  fun replaceSellingPrices(entries: List<SellingPriceEntry>) {
    val validEntries = entries.filter { it.client.id != null }
    val entriesByClientId = validEntries.associateBy { it.client.id!! }

    val iterator = sellingPrices.iterator()
    while (iterator.hasNext()) {
      val current = iterator.next()
      val desired = current.client.id?.let(entriesByClientId::get)
      if (desired == null) {
        iterator.remove()
      } else {
        current.client = desired.client
        current.priceExclTax = desired.priceExclTax
        current.currency = desired.currency
      }
    }

    val existingClientIds = sellingPrices.mapNotNull { it.client.id }.toSet()
    validEntries.forEach { entry ->
      val clientId = entry.client.id ?: return@forEach
      if (clientId !in existingClientIds) {
        sellingPrices.add(
          ProductSellingPrice(this, entry.client, entry.priceExclTax, entry.currency)
        )
      }
    }
  }

  fun replaceSuppliers(clients: Collection<Client>) {
    suppliers.clear()
    suppliers.addAll(clients)
  }

  /** True if this is a physical product that must be ordered from a supplier when sold. */
  fun isMtoProduct(): Boolean = type == ProductType.PRODUCT && mto

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

  data class PurchasePriceEntry(
    val company: ProductPriceCompany,
    val priceExclTax: BigDecimal,
    val currency: String,
  )

  data class SellingPriceEntry(
    val client: Client,
    val priceExclTax: BigDecimal,
    val currency: String,
  )
}
