package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryNoteBRepository : JpaRepository<DeliveryNoteB, Long> {
  fun findByDeletedAtIsNull(): List<DeliveryNoteB>
}
