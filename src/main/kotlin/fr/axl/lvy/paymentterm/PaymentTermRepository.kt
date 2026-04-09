package fr.axl.lvy.paymentterm

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentTermRepository : JpaRepository<PaymentTerm, Long> {
  fun findAllByOrderByLabelAsc(): List<PaymentTerm>

  fun findByLabelIgnoreCase(label: String): Optional<PaymentTerm>
}
