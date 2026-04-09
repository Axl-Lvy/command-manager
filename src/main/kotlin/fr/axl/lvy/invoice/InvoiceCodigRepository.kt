package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceCodigRepository : JpaRepository<InvoiceCodig, Long> {
  fun findByDeletedAtIsNull(): List<InvoiceCodig>
}
