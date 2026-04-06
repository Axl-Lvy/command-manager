package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesBRepository : JpaRepository<SalesB, Long> {

  @Query(
    "SELECT s FROM SalesB s LEFT JOIN FETCH s.salesA LEFT JOIN FETCH s.salesA.client WHERE s.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<SalesB>

  @Query("SELECT s.saleNumber FROM SalesB s WHERE s.saleNumber LIKE 'NST_SO_%'")
  fun findAllSaleNumbers(): List<String>

  fun findBySalesAId(salesAId: Long): SalesB?
}
