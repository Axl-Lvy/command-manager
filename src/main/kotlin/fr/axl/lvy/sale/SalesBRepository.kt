package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesBRepository : JpaRepository<SalesB, Long> {

  @Query(
    "SELECT s FROM SalesB s LEFT JOIN FETCH s.salesA LEFT JOIN FETCH s.salesA.client WHERE s.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<SalesB>

  fun findBySalesAId(salesAId: Long): SalesB?
}
