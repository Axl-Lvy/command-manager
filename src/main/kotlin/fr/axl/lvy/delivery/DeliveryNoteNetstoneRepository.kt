package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DeliveryNoteNetstoneRepository : JpaRepository<DeliveryNoteNetstone, Long> {
  @Query(
    """
      SELECT DISTINCT d FROM DeliveryNoteNetstone d
      LEFT JOIN FETCH d.orderNetstone
      WHERE d.deletedAt IS NULL
    """
  )
  fun findByDeletedAtIsNull(): List<DeliveryNoteNetstone>

  fun findByOrderNetstoneIdAndDeletedAtIsNull(orderNetstoneId: Long): DeliveryNoteNetstone?
}
