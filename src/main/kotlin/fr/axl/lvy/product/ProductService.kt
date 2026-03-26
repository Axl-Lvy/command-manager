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

  @Transactional fun save(product: Product): Product = productRepository.save(product)

  @Transactional
  fun delete(id: Long) {
    productRepository.findById(id).ifPresent { it.softDelete() }
  }
}
