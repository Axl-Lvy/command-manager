package fr.axl.lvy.base

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NumberSequenceService(private val repository: NumberSequenceRepository) {

  @Transactional
  fun nextNumber(entityType: String, prefix: String, padding: Int): String {
    val seq = repository.findForUpdate(entityType)
    val current = seq.nextVal
    seq.nextVal++
    repository.save(seq)
    return prefix + current.toString().padStart(padding, '0')
  }

  companion object {
    const val CLIENT = "CLIENT"
    const val ORDER_A = "ORDER_A"
    const val ORDER_B = "ORDER_B"
    const val SALES_A = "SALES_A"
    const val SALES_B = "SALES_B"
  }
}
