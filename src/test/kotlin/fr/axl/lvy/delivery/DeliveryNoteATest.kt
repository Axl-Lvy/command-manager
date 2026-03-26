package fr.axl.lvy.delivery

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class DeliveryNoteATest {

  @Autowired lateinit var deliveryNoteARepository: DeliveryNoteARepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  private fun createOrderA(number: String, client: Client): OrderA =
    orderARepository.save(OrderA(number, client, LocalDate.of(2026, 3, 1)))

  @Test
  fun save_and_retrieve_delivery_note() {
    val client = createClient("CLI-DA01")
    val order = createOrderA("CA-DA-01", client)
    val note = DeliveryNoteA("BL-2026-0001", order, client)
    note.carrier = "DHL"
    note.trackingNumber = "TRACK-001"
    deliveryNoteARepository.save(note)

    val found = deliveryNoteARepository.findById(note.id!!)
    assertThat(found).isPresent
    assertThat(found.get().deliveryNoteNumber).isEqualTo("BL-2026-0001")
    assertThat(found.get().carrier).isEqualTo("DHL")
    assertThat(found.get().trackingNumber).isEqualTo("TRACK-001")
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-DA02")
    val order = createOrderA("CA-DA-02", client)
    val note = DeliveryNoteA("BL-DEF-01", order, client)

    assertThat(note.status).isEqualTo(DeliveryNoteA.DeliveryNoteAStatus.PREPARED)
    assertThat(note.shippingDate).isNull()
    assertThat(note.deliveryDate).isNull()
    assertThat(note.packageCount).isNull()
    assertThat(note.signedBy).isNull()
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-DA03")
    val order = createOrderA("CA-DA-03", client)
    val note = DeliveryNoteA("BL-DEL-01", order, client)
    deliveryNoteARepository.save(note)

    assertThat(deliveryNoteARepository.findByDeletedAtIsNull()).anyMatch {
      it.deliveryNoteNumber == "BL-DEL-01"
    }

    note.softDelete()
    deliveryNoteARepository.saveAndFlush(note)

    assertThat(deliveryNoteARepository.findByDeletedAtIsNull()).noneMatch {
      it.deliveryNoteNumber == "BL-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-DA04")
    val order = createOrderA("CA-DA-04", client)
    val note = DeliveryNoteA("BL-REST-01", order, client)

    note.softDelete()
    assertThat(note.isDeleted()).isTrue

    note.restore()
    assertThat(note.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-DA05")
    val order = createOrderA("CA-DA-05", client)
    val note = DeliveryNoteA("BL-TS-01", order, client)
    deliveryNoteARepository.save(note)

    assertThat(note.createdAt).isNotNull
    assertThat(note.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-DA06")
    val order = createOrderA("CA-DA-06", client)
    for (status in DeliveryNoteA.DeliveryNoteAStatus.entries) {
      val note = DeliveryNoteA("BL-ST-${status.ordinal}", order, client)
      note.status = status
      deliveryNoteARepository.save(note)

      val found = deliveryNoteARepository.findById(note.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun shipping_and_delivery_fields() {
    val client = createClient("CLI-DA07")
    val order = createOrderA("CA-DA-07", client)
    val note = DeliveryNoteA("BL-SHIP-01", order, client)
    note.shippingDate = LocalDate.of(2026, 3, 10)
    note.deliveryDate = LocalDate.of(2026, 3, 15)
    note.shippingAddress = "456 Shipping Ave"
    note.packageCount = 3
    note.signedBy = "Jean Dupont"
    note.signatureDate = LocalDate.of(2026, 3, 15)
    note.observations = "Handle with care"
    deliveryNoteARepository.save(note)

    val found = deliveryNoteARepository.findById(note.id!!).orElseThrow()
    assertThat(found.shippingDate).isEqualTo(LocalDate.of(2026, 3, 10))
    assertThat(found.deliveryDate).isEqualTo(LocalDate.of(2026, 3, 15))
    assertThat(found.shippingAddress).isEqualTo("456 Shipping Ave")
    assertThat(found.packageCount).isEqualTo(3)
    assertThat(found.signedBy).isEqualTo("Jean Dupont")
    assertThat(found.observations).isEqualTo("Handle with care")
  }
}
