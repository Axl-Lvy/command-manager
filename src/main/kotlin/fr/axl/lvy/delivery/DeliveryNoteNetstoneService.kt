package fr.axl.lvy.delivery

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineService
import io.micrometer.core.instrument.MeterRegistry
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages Netstone delivery notes and their delivered product lines. */
@Service
class DeliveryNoteNetstoneService(
  private val deliveryNoteNetstoneRepository: DeliveryNoteNetstoneRepository,
  private val documentLineService: DocumentLineService,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
) {
  private val deliveriesCreatedCounter = meterRegistry.counter("delivery.netstone")

  companion object {
    private val log = LoggerFactory.getLogger(DeliveryNoteNetstoneService::class.java)
  }

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<DeliveryNoteNetstone> =
    deliveryNoteNetstoneRepository.findById(id)

  @Transactional(readOnly = true)
  fun findByOrderNetstoneId(orderNetstoneId: Long): DeliveryNoteNetstone? =
    deliveryNoteNetstoneRepository.findByOrderNetstoneIdAndDeletedAtIsNull(orderNetstoneId)

  @Transactional(readOnly = true)
  fun findLines(noteId: Long): List<DocumentLine> =
    documentLineService.findLines(DocumentLine.DocumentType.DELIVERY_NETSTONE, noteId)

  @Transactional(readOnly = true)
  fun previewNextDeliveryNoteNumber(): String =
    numberSequenceService.previewNextNumber(NumberSequenceService.DELIVERY_NETSTONE)

  @Transactional
  fun save(note: DeliveryNoteNetstone): DeliveryNoteNetstone {
    val isNew = note.deliveryNoteNumber.isBlank()
    if (isNew) {
      note.deliveryNoteNumber =
        numberSequenceService.nextNumber(NumberSequenceService.DELIVERY_NETSTONE)
    }
    val saved = deliveryNoteNetstoneRepository.save(note)
    if (isNew) {
      deliveriesCreatedCounter.increment()
      MDC.put("deliveryNoteNumber", saved.deliveryNoteNumber)
      MDC.put("orderNumber", saved.orderNetstone.orderNumber)
      try {
        log.info(
          "DeliveryNoteNetstone {} created for order {}",
          saved.deliveryNoteNumber,
          saved.orderNetstone.orderNumber,
        )
      } finally {
        MDC.remove("deliveryNoteNumber")
        MDC.remove("orderNumber")
      }
    }
    return saved
  }

  @Transactional
  fun saveWithLines(note: DeliveryNoteNetstone, lines: List<DocumentLine>): DeliveryNoteNetstone {
    val saved = save(note)
    documentLineService.replaceLines(DocumentLine.DocumentType.DELIVERY_NETSTONE, saved.id!!, lines)
    return saved
  }
}
