package fr.axl.lvy.delivery

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.order.OrderARepository
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeliveryNoteAService(
  private val deliveryNoteARepository: DeliveryNoteARepository,
  private val orderARepository: OrderARepository,
  private val numberSequenceService: NumberSequenceService,
) {

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<DeliveryNoteA> = deliveryNoteARepository.findById(id)

  @Transactional(readOnly = true)
  fun findByOrderAId(orderAId: Long): DeliveryNoteA? =
    deliveryNoteARepository.findByOrderAIdAndDeletedAtIsNull(orderAId)

  @Transactional
  fun save(note: DeliveryNoteA): DeliveryNoteA {
    if (note.deliveryNoteNumber.isBlank()) {
      note.deliveryNoteNumber = numberSequenceService.nextNumber(NumberSequenceService.DELIVERY_A)
    }
    if (note.shippingAddress.isNullOrBlank()) {
      note.shippingAddress = note.orderA.shippingAddress
    }

    val saved = deliveryNoteARepository.save(note)
    val order = saved.orderA
    if (order.deliveryNote?.id != saved.id) {
      order.deliveryNote = saved
      orderARepository.save(order)
    }
    return saved
  }
}
