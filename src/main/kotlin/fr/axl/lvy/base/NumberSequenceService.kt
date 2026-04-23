package fr.axl.lvy.base

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Generates sequential, prefixed business reference numbers (e.g. "C000001" for clients,
 * "CoD_PO_001" for Codig orders). Uses pessimistic locking to guarantee uniqueness.
 */
@Service
class NumberSequenceService(
  private val repository: NumberSequenceRepository,
  private val meterRegistry: MeterRegistry,
) {
  init {
    // Pre-register one counter per sequence type so they appear in Prometheus at startup
    // with value 0, even if no sequences have been generated yet in this process.
    CONFIGS.keys.forEach { meterRegistry.counter("number.sequence.generated", "type", it) }
  }

  @Transactional
  fun nextNumber(entityType: String): String {
    val config =
      CONFIGS[entityType] ?: throw IllegalArgumentException("Unknown entity type: $entityType")
    return nextNumber(entityType, config.prefix, config.padding)
  }

  @Transactional(readOnly = true)
  fun previewNextNumber(entityType: String): String {
    val config =
      CONFIGS[entityType] ?: throw IllegalArgumentException("Unknown entity type: $entityType")
    val current = repository.findById(entityType).map { it.nextVal }.orElse(1)
    return config.prefix + current.toString().padStart(config.padding, '0')
  }

  @Transactional
  fun nextNumber(entityType: String, prefix: String, padding: Int): String {
    val seq = repository.findForUpdate(entityType) ?: repository.save(NumberSequence(entityType))
    val current = seq.nextVal
    seq.nextVal++
    repository.save(seq)
    meterRegistry.counter("number.sequence.generated", "type", entityType).increment()
    return prefix + current.toString().padStart(padding, '0')
  }

  data class SequenceConfig(val prefix: String, val padding: Int)

  companion object {
    const val CLIENT = "CLIENT"
    const val ORDER_CODIG = "ORDER_CODIG"
    const val ORDER_NETSTONE = "ORDER_NETSTONE"
    const val SALES_CODIG = "SALES_CODIG"
    const val SALES_NETSTONE = "SALES_NETSTONE"
    const val DELIVERY_CODIG = "DELIVERY_CODIG"
    const val DELIVERY_NETSTONE = "DELIVERY_NETSTONE"

    val CONFIGS =
      mapOf(
        CLIENT to SequenceConfig("C", 6),
        ORDER_CODIG to SequenceConfig("CoD_PO_", 3),
        ORDER_NETSTONE to SequenceConfig("NST_PO_", 3),
        SALES_CODIG to SequenceConfig("CoD_SO_", 3),
        SALES_NETSTONE to SequenceConfig("NST_SO_", 3),
        DELIVERY_CODIG to SequenceConfig("CoD/OUT/", 3),
        DELIVERY_NETSTONE to SequenceConfig("Netst/OUT/", 3),
      )
  }
}
