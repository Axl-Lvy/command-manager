package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DeliveryNoteCodigRepository : JpaRepository<DeliveryNoteCodig, Long> {
  @Query(
    """
      SELECT DISTINCT d FROM DeliveryNoteCodig d
      LEFT JOIN FETCH d.orderCodig
      LEFT JOIN FETCH d.client
      WHERE d.deletedAt IS NULL
    """
  )
  fun findByDeletedAtIsNull(): List<DeliveryNoteCodig>

  fun findByOrderCodigIdAndDeletedAtIsNull(orderCodigId: Long): DeliveryNoteCodig?
}
