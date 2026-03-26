package fr.axl.lvy.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
  fun findByDeletedAtIsNullAndActiveTrue(): List<Product>

  fun findByDeletedAtIsNull(): List<Product>
}
