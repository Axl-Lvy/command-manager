package fr.axl.lvy.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderNetstoneRepository : JpaRepository<OrderNetstone, Long> {

  @Query(
    "SELECT o FROM OrderNetstone o LEFT JOIN FETCH o.orderCodig LEFT JOIN FETCH o.orderCodig.client LEFT JOIN FETCH o.supplier LEFT JOIN FETCH o.paymentTerm LEFT JOIN FETCH o.fiscalPosition WHERE o.deletedAt IS NULL"
  )
  fun findByDeletedAtIsNull(): List<OrderNetstone>

  fun findByDeletedAtIsNull(pageable: Pageable): Page<OrderNetstone>

  @Query(
    """
      SELECT o
      FROM OrderNetstone o
      LEFT JOIN FETCH o.orderCodig
      LEFT JOIN FETCH o.orderCodig.client
      LEFT JOIN FETCH o.supplier
      LEFT JOIN FETCH o.paymentTerm
      LEFT JOIN FETCH o.fiscalPosition
      WHERE o.id = :id
    """
  )
  fun findDetailedById(id: Long): OrderNetstone?

  fun findByOrderCodigId(orderCodigId: Long): List<OrderNetstone>
}
