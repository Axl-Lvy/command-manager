package fr.axl.lvy.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Long> {
  @Query(
    """
      SELECT DISTINCT p
      FROM Product p
      LEFT JOIN FETCH p.clientProductCodes c
      LEFT JOIN FETCH c.client
      LEFT JOIN FETCH p.suppliers s
      WHERE p.deletedAt IS NULL AND p.active = true
    """
  )
  fun findByDeletedAtIsNullAndActiveTrue(): List<Product>

  @Query(
    """
      SELECT DISTINCT p
      FROM Product p
      LEFT JOIN FETCH p.clientProductCodes c
      LEFT JOIN FETCH c.client
      LEFT JOIN FETCH p.suppliers s
      WHERE p.deletedAt IS NULL
    """
  )
  fun findByDeletedAtIsNull(): List<Product>

  fun findByDeletedAtIsNull(pageable: Pageable): Page<Product>

  @Query(
    """
      SELECT DISTINCT p
      FROM Product p
      LEFT JOIN FETCH p.clientProductCodes c
      LEFT JOIN FETCH c.client
      LEFT JOIN FETCH p.suppliers s
      WHERE p.id = :id
    """
  )
  fun findDetailedById(id: Long): Product?

  @Query("SELECT p.reference FROM Product p") fun findAllReferences(): List<String>
}
