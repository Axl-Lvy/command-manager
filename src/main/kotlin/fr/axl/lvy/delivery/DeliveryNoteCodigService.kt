package fr.axl.lvy.delivery

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.order.OrderCodigRepository
import io.micrometer.core.instrument.MeterRegistry
import java.util.Optional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages Codig delivery notes. Links them back to the parent order on save. */
@Service
class DeliveryNoteCodigService(
  private val deliveryNoteCodigRepository: DeliveryNoteCodigRepository,
  private val orderCodigRepository: OrderCodigRepository,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
) {
  private val deliveriesCreatedCounter = meterRegistry.counter("delivery.codig")

  companion object {
    private val log = LoggerFactory.getLogger(DeliveryNoteCodigService::class.java)
  }

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<DeliveryNoteCodig> = deliveryNoteCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): DeliveryNoteCodig? =
    deliveryNoteCodigRepository.findByOrderCodigIdAndDeletedAtIsNull(orderCodigId)

  @Transactional
  fun save(note: DeliveryNoteCodig): DeliveryNoteCodig {
    val isNew = note.deliveryNoteNumber.isBlank()
    if (isNew) {
      note.deliveryNoteNumber =
        numberSequenceService.nextNumber(NumberSequenceService.DELIVERY_CODIG)
    }
    if (note.shippingAddress.isNullOrBlank()) {
      note.shippingAddress = note.orderCodig.shippingAddress
    }

    val saved = deliveryNoteCodigRepository.save(note)
    val order = saved.orderCodig
    if (order.deliveryNote?.id != saved.id) {
      order.deliveryNote = saved
      orderCodigRepository.save(order)
    }

    if (isNew) {
      deliveriesCreatedCounter.increment()
      MDC.put("deliveryNoteNumber", saved.deliveryNoteNumber)
      MDC.put("orderNumber", saved.orderCodig.orderNumber)
      try {
        log.info(
          "DeliveryNoteCodig {} created for order {}",
          saved.deliveryNoteNumber,
          saved.orderCodig.orderNumber,
        )
      } finally {
        MDC.remove("deliveryNoteNumber")
        MDC.remove("orderNumber")
      }
    }
    return saved
  }
}
