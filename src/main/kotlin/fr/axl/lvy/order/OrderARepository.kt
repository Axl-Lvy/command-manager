package fr.axl.lvy.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderARepository : JpaRepository<OrderA, Long> {

  @Query("SELECT o FROM OrderA o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<OrderA>

  @Query("SELECT o.orderNumber FROM OrderA o WHERE o.orderNumber LIKE 'CoD_PO_%'")
  fun findAllOrderNumbers(): List<String>
}
