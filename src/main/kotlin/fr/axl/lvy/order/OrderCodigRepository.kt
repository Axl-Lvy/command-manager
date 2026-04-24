package fr.axl.lvy.order

import java.time.Instant
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderCodigRepository : JpaRepository<OrderCodig, Long> {

  @Query("SELECT o FROM OrderCodig o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<OrderCodig>

  /**
   * Counts CONFIRMED orders whose [BaseEntity.updatedAt] predates [threshold]. Used by
   * [StuckOrderHealthIndicator] to surface orders that may be stuck awaiting delivery.
   */
  @Query(
    "SELECT COUNT(o) FROM OrderCodig o WHERE o.status = :status AND o.updatedAt < :threshold AND o.deletedAt IS NULL"
  )
  fun countConfirmedNotUpdatedSince(
    @Param("status") status: OrderCodig.OrderCodigStatus,
    @Param("threshold") threshold: Instant,
  ): Long

  @Query(
    """
      SELECT o
      FROM OrderCodig o
      LEFT JOIN FETCH o.client
      LEFT JOIN FETCH o.paymentTerm
      LEFT JOIN FETCH o.fiscalPosition
      WHERE o.id = :id
    """
  )
  fun findDetailedById(id: Long): OrderCodig?

  /**
   * Paginated search for order pickers: matches orderNumber or client name (case-insensitive
   * substring). Used by ComboBox lazy fetch callbacks.
   */
  @Query(
    """
      SELECT o FROM OrderCodig o
      LEFT JOIN FETCH o.client c
      WHERE o.deletedAt IS NULL
        AND (
          :filter = ''
          OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :filter, '%'))
          OR LOWER(c.name) LIKE LOWER(CONCAT('%', :filter, '%'))
        )
      ORDER BY o.orderNumber DESC
    """
  )
  fun searchActive(@Param("filter") filter: String, pageable: Pageable): List<OrderCodig>

  @Query(
    """
      SELECT COUNT(o) FROM OrderCodig o
      WHERE o.deletedAt IS NULL
        AND (
          :filter = ''
          OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :filter, '%'))
          OR LOWER(o.client.name) LIKE LOWER(CONCAT('%', :filter, '%'))
        )
    """
  )
  fun countActive(@Param("filter") filter: String): Long
}
