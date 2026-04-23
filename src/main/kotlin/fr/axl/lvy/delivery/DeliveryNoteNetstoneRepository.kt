package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryNoteNetstoneRepository : JpaRepository<DeliveryNoteNetstone, Long> {
  fun findByDeletedAtIsNull(): List<DeliveryNoteNetstone>

  fun findByOrderNetstoneIdAndDeletedAtIsNull(orderNetstoneId: Long): DeliveryNoteNetstone?
}
