package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesNetstoneRepository : JpaRepository<SalesNetstone, Long> {

  @Query(
    "SELECT s FROM SalesNetstone s LEFT JOIN FETCH s.salesCodig sc LEFT JOIN FETCH sc.client LEFT JOIN FETCH sc.orderCodig oc LEFT JOIN FETCH oc.client LEFT JOIN FETCH s.fiscalPosition LEFT JOIN FETCH s.orderNetstone WHERE s.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<SalesNetstone>

  @Query(
    """
      SELECT s
      FROM SalesNetstone s
      LEFT JOIN FETCH s.salesCodig sc
      LEFT JOIN FETCH sc.client
      LEFT JOIN FETCH sc.orderCodig oc
      LEFT JOIN FETCH oc.client
      LEFT JOIN FETCH s.fiscalPosition
      LEFT JOIN FETCH s.orderNetstone
      WHERE s.id = :id
    """
  )
  fun findDetailedById(id: Long): SalesNetstone?

  @Query(
    """
      SELECT s
      FROM SalesNetstone s
      LEFT JOIN FETCH s.salesCodig sc
      LEFT JOIN FETCH sc.client
      LEFT JOIN FETCH sc.orderCodig oc
      LEFT JOIN FETCH oc.client
      LEFT JOIN FETCH s.fiscalPosition
      LEFT JOIN FETCH s.orderNetstone
      WHERE sc.id = :salesCodigId AND s.deletedAt IS NULL
    """
  )
  fun findBySalesCodigId(salesCodigId: Long): SalesNetstone?

  @Query(
    """
      SELECT s
      FROM SalesNetstone s
      LEFT JOIN FETCH s.salesCodig sc
      LEFT JOIN FETCH sc.client
      LEFT JOIN FETCH sc.orderCodig oc
      LEFT JOIN FETCH oc.client
      LEFT JOIN FETCH s.fiscalPosition
      LEFT JOIN FETCH s.orderNetstone
      WHERE oc.id = :orderCodigId AND s.deletedAt IS NULL
    """
  )
  fun findByOrderCodigId(orderCodigId: Long): SalesNetstone?
}
