package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface InvoiceCodigRepository : JpaRepository<InvoiceCodig, Long> {
  @Query(
    """
      SELECT DISTINCT i FROM InvoiceCodig i
      LEFT JOIN FETCH i.client
      LEFT JOIN FETCH i.orderCodig
      LEFT JOIN FETCH i.deliveryNote
      LEFT JOIN FETCH i.creditNote
      WHERE i.deletedAt IS NULL
    """
  )
  fun findByDeletedAtIsNull(): List<InvoiceCodig>
}
