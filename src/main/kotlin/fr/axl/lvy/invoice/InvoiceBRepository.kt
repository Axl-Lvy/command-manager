package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceBRepository : JpaRepository<InvoiceB, Long> {
  fun findByDeletedAtIsNull(): List<InvoiceB>
}
