package fr.axl.lvy.product

import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(private val productRepository: ProductRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<Product> = productRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findActive(): List<Product> = productRepository.findByDeletedAtIsNullAndActiveTrue()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Product> = productRepository.findById(id)

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<Product> = Optional.ofNullable(productRepository.findDetailedById(id))

  @Transactional
  fun save(product: Product): Product {
    if (product.reference.isBlank()) {
      product.reference = generateNextReference()
    }
    return productRepository.save(product)
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
