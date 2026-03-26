package fr.axl.lvy.delivery

import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryNoteARepository : JpaRepository<DeliveryNoteA, Long> {
  fun findByDeletedAtIsNull(): List<DeliveryNoteA>
}
