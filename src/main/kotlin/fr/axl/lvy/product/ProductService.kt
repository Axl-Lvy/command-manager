package fr.axl.lvy.product

import fr.axl.lvy.client.ClientRepository
import java.util.Optional
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
    Optional.ofNullable(productRepository.findDetailedById(id))

  @Transactional(readOnly = true)
  fun findClientProductCode(productId: Long, clientId: Long): String? =
    productClientCodeRepository.findCodeByProductIdAndClientId(productId, clientId)

  @Transactional(readOnly = true)
  fun findFirstClientProductCode(productId: Long): String? =
    productClientCodeRepository.findCodesByProductId(productId).firstOrNull()

  @Transactional
  fun save(product: Product): Product {
    if (product.clientProductCodes.isNotEmpty()) {
      val managedEntries =
        product.clientProductCodes.map { clientProductCode ->
          clientRepository.getReferenceById(clientProductCode.client.id!!) to clientProductCode.code
        }
      product.replaceClientProductCodes(managedEntries)
    }
    if (product.suppliers.isNotEmpty()) {
      val managedSuppliers = product.suppliers.map { clientRepository.getReferenceById(it.id!!) }
      product.replaceSuppliers(managedSuppliers)
    }
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
