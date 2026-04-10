package fr.axl.lvy.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderCodigRepository : JpaRepository<OrderCodig, Long> {

  @Query("SELECT o FROM OrderCodig o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<OrderCodig>

  @Query(
    """
      SELECT o
      FROM OrderCodig o
      LEFT JOIN FETCH o.client
      WHERE o.id = :id
    """
  )
  fun findDetailedById(id: Long): OrderCodig?
}
