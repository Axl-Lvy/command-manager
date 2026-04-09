package fr.axl.lvy.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderNetstoneRepository : JpaRepository<OrderNetstone, Long> {

  @Query(
    "SELECT o FROM OrderNetstone o LEFT JOIN FETCH o.orderCodig LEFT JOIN FETCH o.orderCodig.client WHERE o.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<OrderNetstone>

  fun findByOrderCodigId(orderCodigId: Long): List<OrderNetstone>
}
