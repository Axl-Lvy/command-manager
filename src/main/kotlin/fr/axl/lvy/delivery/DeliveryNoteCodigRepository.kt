package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryNoteCodigRepository : JpaRepository<DeliveryNoteCodig, Long> {
  fun findByDeletedAtIsNull(): List<DeliveryNoteCodig>

  fun findByOrderCodigIdAndDeletedAtIsNull(orderCodigId: Long): DeliveryNoteCodig?
}
