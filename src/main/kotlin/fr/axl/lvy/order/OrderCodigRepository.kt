package fr.axl.lvy.order

import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderCodigRepository : JpaRepository<OrderCodig, Long> {

  @Query("SELECT o FROM OrderCodig o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<OrderCodig>

  fun findByDeletedAtIsNull(pageable: Pageable): Page<OrderCodig>

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
}
