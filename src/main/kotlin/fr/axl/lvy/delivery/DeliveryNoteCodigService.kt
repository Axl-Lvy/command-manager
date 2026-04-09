package fr.axl.lvy.delivery

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.order.OrderCodigRepository
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeliveryNoteCodigService(
  private val deliveryNoteCodigRepository: DeliveryNoteCodigRepository,
  private val orderCodigRepository: OrderCodigRepository,
  private val numberSequenceService: NumberSequenceService,
) {

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<DeliveryNoteCodig> = deliveryNoteCodigRepository.findById(id)

  @Transactional(readOnly = true)
  fun findByOrderCodigId(orderCodigId: Long): DeliveryNoteCodig? =
    deliveryNoteCodigRepository.findByOrderCodigIdAndDeletedAtIsNull(orderCodigId)

  @Transactional
  fun save(note: DeliveryNoteCodig): DeliveryNoteCodig {
    if (note.deliveryNoteNumber.isBlank()) {
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
    return saved
  }
}
