package fr.axl.lvy.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderBRepository : JpaRepository<OrderB, Long> {

  @Query(
    "SELECT o FROM OrderB o LEFT JOIN FETCH o.orderA LEFT JOIN FETCH o.orderA.client WHERE o.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<OrderB>

  fun findByOrderAId(orderAId: Long): List<OrderB>

  @Query("SELECT o.orderNumber FROM OrderB o WHERE o.orderNumber LIKE 'NST_PO_%'")
  fun findAllOrderNumbers(): List<String>
}
