package fr.axl.lvy.sale

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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
      LEFT JOIN FETCH s.salesNetstone sn
      LEFT JOIN FETCH sn.orderNetstone
      WHERE s.id = :id
    """
  )
  fun findDetailedById(id: Long): SalesCodig?

  @Query(
    """
      SELECT s
      FROM SalesCodig s
      LEFT JOIN FETCH s.client
      LEFT JOIN FETCH s.orderCodig o
      WHERE o.id = :orderCodigId
        AND s.deletedAt IS NULL
    """
  )
  fun findByOrderCodigId(orderCodigId: Long): SalesCodig?

  /**
   * Paginated search of non-deleted sales that already have a generated Codig order. Matches by
   * saleNumber or client name (case-insensitive). Used by ComboBox lazy fetch callbacks.
   */
  @Query(
    """
      SELECT s FROM SalesCodig s
      LEFT JOIN FETCH s.client c
      LEFT JOIN FETCH s.orderCodig o
      LEFT JOIN FETCH o.client
      WHERE s.deletedAt IS NULL
        AND s.orderCodig IS NOT NULL
        AND (
          :filter = ''
          OR LOWER(s.saleNumber) LIKE LOWER(CONCAT('%', :filter, '%'))
          OR LOWER(c.name) LIKE LOWER(CONCAT('%', :filter, '%'))
        )
      ORDER BY s.saleNumber DESC
    """
  )
  fun searchActiveWithLinkedOrder(
    @Param("filter") filter: String,
    pageable: Pageable,
  ): List<SalesCodig>

  @Query(
    """
      SELECT COUNT(s) FROM SalesCodig s
      WHERE s.deletedAt IS NULL
        AND s.orderCodig IS NOT NULL
        AND (
          :filter = ''
          OR LOWER(s.saleNumber) LIKE LOWER(CONCAT('%', :filter, '%'))
          OR LOWER(s.client.name) LIKE LOWER(CONCAT('%', :filter, '%'))
        )
    """
  )
  fun countActiveWithLinkedOrder(@Param("filter") filter: String): Long
}
