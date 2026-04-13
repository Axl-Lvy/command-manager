package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesCodigRepository : JpaRepository<SalesCodig, Long> {

  @Query("SELECT s FROM SalesCodig s LEFT JOIN FETCH s.client WHERE s.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<SalesCodig>

  @Query(
    """
      SELECT s
      FROM SalesCodig s
      LEFT JOIN FETCH s.client
      LEFT JOIN FETCH s.orderCodig o
      LEFT JOIN FETCH o.client
      WHERE s.deletedAt IS NULL AND s.orderCodig IS NOT NULL
    """
  )
  fun findByDeletedAtIsNullAndOrderCodigIsNotNull(): List<SalesCodig>

  @Query(
    """
      SELECT s
      FROM SalesCodig s
      LEFT JOIN FETCH s.client
      LEFT JOIN FETCH s.paymentTerm
      LEFT JOIN FETCH s.fiscalPosition
      LEFT JOIN FETCH s.orderCodig o
      LEFT JOIN FETCH o.deliveryNote
      WHERE s.id = :id
    """
  )
  fun findDetailedById(id: Long): SalesCodig?

  fun findByOrderCodigId(orderCodigId: Long): SalesCodig?
}
