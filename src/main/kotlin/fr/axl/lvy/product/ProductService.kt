package fr.axl.lvy.product

import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.client.Client
import fr.axl.lvy.documentline.DocumentLine
import java.math.BigDecimal
import java.util.Optional
import org.hibernate.Hibernate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages the product catalog. Auto-generates a unique reference (e.g. "P000001") on first save.
 */
@Service
class ProductService(
  private val productRepository: ProductRepository,
  private val productClientCodeRepository: ProductClientCodeRepository,
  private val productPurchasePriceRepository: ProductPurchasePriceRepository,
  private val productSellingPriceRepository: ProductSellingPriceRepository,
  private val clientRepository: ClientRepository,
) {

  @Transactional(readOnly = true)
  fun findAll(): List<Product> = productRepository.findByDeletedAtIsNull()

  /** Paginated variant for Vaadin lazy-loading grids. */
  @Transactional(readOnly = true)
  fun findAll(pageable: Pageable): Page<Product> = productRepository.findByDeletedAtIsNull(pageable)

  @Transactional(readOnly = true)
  fun findActive(): List<Product> = productRepository.findByDeletedAtIsNullAndActiveTrue()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Product> = productRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<Product> =
    Optional.ofNullable(productRepository.findDetailedById(id)).map { product ->
      Hibernate.initialize(product.purchasePrices)
      Hibernate.initialize(product.sellingPrices)
      product.sellingPrices.forEach { Hibernate.initialize(it.client) }
      product
    }

  @Transactional(readOnly = true)
  fun findClientProductCode(productId: Long, clientId: Long): String? =
    productClientCodeRepository.findCodeByProductIdAndClientId(productId, clientId)

  @Transactional(readOnly = true)
  fun findFirstClientProductCode(productId: Long): String? =
    productClientCodeRepository.findCodesByProductId(productId).firstOrNull()

  /** Resolves the unit price to prefill when a product is inserted into a document. */
  @Transactional(readOnly = true)
  fun resolveUnitPrice(
    documentType: DocumentLine.DocumentType,
    product: Product,
    client: Client? = null,
    usePurchasePrice: Boolean = false,
  ): BigDecimal? =
    when {
      usePurchasePrice || documentType == DocumentLine.DocumentType.ORDER_CODIG ->
        findPurchasePrice(product.id, ProductPriceCompany.CODIG)?.priceExclTax
      documentType == DocumentLine.DocumentType.SALES_CODIG ||
        documentType == DocumentLine.DocumentType.INVOICE_CODIG ->
        client?.id?.let { clientId -> findSellingPrice(product.id, clientId)?.priceExclTax }
      documentType == DocumentLine.DocumentType.SALES_NETSTONE ||
        documentType == DocumentLine.DocumentType.ORDER_NETSTONE ||
        documentType == DocumentLine.DocumentType.INVOICE_NETSTONE ||
        documentType == DocumentLine.DocumentType.DELIVERY_NETSTONE ->
        findPurchasePrice(product.id, ProductPriceCompany.NETSTONE)?.priceExclTax
      else -> null
    }

  /** Resolves the currency that matches the unit price used for the given document context. */
  @Transactional(readOnly = true)
  fun resolveCurrency(
    documentType: DocumentLine.DocumentType,
    product: Product,
    client: Client? = null,
    usePurchasePrice: Boolean = false,
  ): String? =
    when {
      usePurchasePrice || documentType == DocumentLine.DocumentType.ORDER_CODIG ->
        findPurchasePrice(product.id, ProductPriceCompany.CODIG)?.currency
      documentType == DocumentLine.DocumentType.SALES_CODIG ||
        documentType == DocumentLine.DocumentType.INVOICE_CODIG ->
        client?.id?.let { clientId -> findSellingPrice(product.id, clientId)?.currency }
      documentType == DocumentLine.DocumentType.SALES_NETSTONE ||
        documentType == DocumentLine.DocumentType.ORDER_NETSTONE ||
        documentType == DocumentLine.DocumentType.INVOICE_NETSTONE ||
        documentType == DocumentLine.DocumentType.DELIVERY_NETSTONE ->
        findPurchasePrice(product.id, ProductPriceCompany.NETSTONE)?.currency
      else -> null
    }

  /** Returns the current purchase price configured for [company]. */
  @Transactional(readOnly = true)
  fun findPurchasePrice(productId: Long?, company: ProductPriceCompany): ProductPurchasePrice? =
    productId?.let { productPurchasePriceRepository.findByProductIdAndCompany(it, company) }

  /** Returns the customer-specific selling price configured for [clientId]. */
  @Transactional(readOnly = true)
  fun findSellingPrice(productId: Long?, clientId: Long): ProductSellingPrice? =
    productId?.let { productSellingPriceRepository.findByProductIdAndClientId(it, clientId) }

  @Transactional
  fun save(product: Product): Product {
    val managedClientCodes =
      product.clientProductCodes
        .mapNotNull { clientProductCode ->
          clientProductCode.client.id?.let { clientId ->
            clientRepository.getReferenceById(clientId) to clientProductCode.code
          }
        }
    product.replaceClientProductCodes(managedClientCodes)

    val managedSuppliers = product.suppliers.mapNotNull { it.id?.let(clientRepository::getReferenceById) }
    product.replaceSuppliers(managedSuppliers)

    val purchasePriceEntries =
      product.purchasePrices.map { Product.PurchasePriceEntry(it.company, it.priceExclTax, it.currency) }
    product.replacePurchasePrices(purchasePriceEntries)

    val sellingPriceEntries =
      product.sellingPrices.mapNotNull { price ->
        price.client.id?.let { clientId ->
          Product.SellingPriceEntry(
            clientRepository.getReferenceById(clientId),
            price.priceExclTax,
            price.currency,
          )
        }
      }
    product.replaceSellingPrices(sellingPriceEntries)

    if (product.reference.isBlank()) {
      product.reference = generateNextReference()
    }
    return productRepository.save(product)
  }

  @Transactional
  fun archive(id: Long) {
    productRepository.findById(id).ifPresent { it.active = false }
  }

  @Transactional
  fun delete(id: Long) {
    productRepository.findById(id).ifPresent { it.softDelete() }
  }

  private fun generateNextReference(): String {
    val nextNumber =
      productRepository
        .findAllReferences()
        .mapNotNull { reference ->
          REFERENCE_REGEX.matchEntire(reference)?.groupValues?.get(1)?.toIntOrNull()
        }
        .maxOrNull()
        ?.plus(1) ?: 1
    return REFERENCE_PREFIX + nextNumber.toString().padStart(6, '0')
  }

  companion object {
    private const val REFERENCE_PREFIX = "P"
    private val REFERENCE_REGEX = Regex("""P(\d{6})""")
  }
}
