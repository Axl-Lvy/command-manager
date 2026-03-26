package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceARepository : JpaRepository<InvoiceA, Long> {
  fun findByDeletedAtIsNull(): List<InvoiceA>
}
