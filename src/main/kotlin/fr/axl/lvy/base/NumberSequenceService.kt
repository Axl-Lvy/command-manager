package fr.axl.lvy.base

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NumberSequenceService(private val repository: NumberSequenceRepository) {

  @Transactional
  fun nextNumber(entityType: String): String {
    val config =
      CONFIGS[entityType] ?: throw IllegalArgumentException("Unknown entity type: $entityType")
    return nextNumber(entityType, config.prefix, config.padding)
  }

  @Transactional
  fun nextNumber(entityType: String, prefix: String, padding: Int): String {
    val seq = repository.findForUpdate(entityType) ?: repository.save(NumberSequence(entityType))
    val current = seq.nextVal
    seq.nextVal++
    repository.save(seq)
    return prefix + current.toString().padStart(padding, '0')
  }

  data class SequenceConfig(val prefix: String, val padding: Int)

  companion object {
    const val CLIENT = "CLIENT"
    const val ORDER_A = "ORDER_A"
    const val ORDER_B = "ORDER_B"
    const val SALES_A = "SALES_A"
    const val SALES_B = "SALES_B"

    val CONFIGS =
      mapOf(
        CLIENT to SequenceConfig("C", 6),
        ORDER_A to SequenceConfig("CoD_PO_", 3),
        ORDER_B to SequenceConfig("NST_PO_", 3),
        SALES_A to SequenceConfig("CoD_SO_", 3),
        SALES_B to SequenceConfig("NST_SO_", 3),
      )
  }
}
