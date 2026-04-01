package fr.axl.lvy.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Long> {
  fun findByDeletedAtIsNullAndActiveTrue(): List<Product>

  fun findByDeletedAtIsNull(): List<Product>

  @Query("SELECT p.reference FROM Product p") fun findAllReferences(): List<String>
}
