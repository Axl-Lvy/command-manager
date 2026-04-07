package fr.axl.lvy.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OrderARepository : JpaRepository<OrderA, Long> {

  @Query("SELECT o FROM OrderA o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<OrderA>
}
