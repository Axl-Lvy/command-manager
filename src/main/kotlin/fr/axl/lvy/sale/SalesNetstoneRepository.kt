package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesNetstoneRepository : JpaRepository<SalesNetstone, Long> {

  @Query(
    "SELECT s FROM SalesNetstone s LEFT JOIN FETCH s.salesCodig LEFT JOIN FETCH s.salesCodig.client WHERE s.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<SalesNetstone>

  fun findBySalesCodigId(salesCodigId: Long): SalesNetstone?
}
