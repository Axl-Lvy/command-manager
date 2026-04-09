package fr.axl.lvy.paymentterm

import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentTermService(private val paymentTermRepository: PaymentTermRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<PaymentTerm> = paymentTermRepository.findAllByOrderByLabelAsc()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<PaymentTerm> = paymentTermRepository.findById(id)

  @Transactional
  fun save(paymentTerm: PaymentTerm): PaymentTerm {
    paymentTerm.label = paymentTerm.label.trim()
    val existing = paymentTermRepository.findByLabelIgnoreCase(paymentTerm.label)
    if (existing.isPresent && existing.get().id != paymentTerm.id) {
      throw IllegalArgumentException(
        "Un délai de paiement avec le libellé '${paymentTerm.label}' existe déjà"
      )
    }
    return paymentTermRepository.save(paymentTerm)
  }

  @Transactional
  fun delete(id: Long) {
    if (paymentTermRepository.existsById(id)) {
      paymentTermRepository.deleteById(id)
    }
  }
}
