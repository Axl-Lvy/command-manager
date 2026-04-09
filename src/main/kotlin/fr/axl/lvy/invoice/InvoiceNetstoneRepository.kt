package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceNetstoneRepository : JpaRepository<InvoiceNetstone, Long> {
  fun findByDeletedAtIsNull(): List<InvoiceNetstone>
}
