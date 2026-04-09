package fr.axl.lvy.delivery

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class DeliveryNoteCodigTest {

  @Autowired lateinit var deliveryNoteCodigRepository: DeliveryNoteCodigRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  private fun createOrderCodig(number: String, client: Client): OrderCodig =
    orderCodigRepository.save(OrderCodig(number, client, LocalDate.of(2026, 3, 1)))

  @Test
  fun save_and_retrieve_delivery_note() {
    val client = createClient("CLI-DA01")
    val order = createOrderCodig("CA-DA-01", client)
    val note = DeliveryNoteCodig("BL-2026-0001", order, client)
    note.carrier = "DHL"
    note.trackingNumber = "TRACK-001"
    deliveryNoteCodigRepository.save(note)

    val found = deliveryNoteCodigRepository.findById(note.id!!)
    assertThat(found).isPresent
    assertThat(found.get().deliveryNoteNumber).isEqualTo("BL-2026-0001")
    assertThat(found.get().carrier).isEqualTo("DHL")
    assertThat(found.get().trackingNumber).isEqualTo("TRACK-001")
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-DA02")
    val order = createOrderCodig("CA-DA-02", client)
    val note = DeliveryNoteCodig("BL-DEF-01", order, client)

    assertThat(note.status).isEqualTo(DeliveryNoteCodig.DeliveryNoteCodigStatus.PREPARED)
    assertThat(note.shippingDate).isNull()
    assertThat(note.deliveryDate).isNull()
    assertThat(note.packageCount).isNull()
    assertThat(note.signedBy).isNull()
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-DA03")
    val order = createOrderCodig("CA-DA-03", client)
    val note = DeliveryNoteCodig("BL-DEL-01", order, client)
    deliveryNoteCodigRepository.save(note)

    assertThat(deliveryNoteCodigRepository.findByDeletedAtIsNull()).anyMatch {
      it.deliveryNoteNumber == "BL-DEL-01"
    }

    note.softDelete()
    deliveryNoteCodigRepository.saveAndFlush(note)

    assertThat(deliveryNoteCodigRepository.findByDeletedAtIsNull()).noneMatch {
      it.deliveryNoteNumber == "BL-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-DA04")
    val order = createOrderCodig("CA-DA-04", client)
    val note = DeliveryNoteCodig("BL-REST-01", order, client)

    note.softDelete()
    assertThat(note.isDeleted()).isTrue

    note.restore()
    assertThat(note.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-DA05")
    val order = createOrderCodig("CA-DA-05", client)
    val note = DeliveryNoteCodig("BL-TS-01", order, client)
    deliveryNoteCodigRepository.save(note)

    assertThat(note.createdAt).isNotNull
    assertThat(note.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-DA06")
    val order = createOrderCodig("CA-DA-06", client)
    for (status in DeliveryNoteCodig.DeliveryNoteCodigStatus.entries) {
      val note = DeliveryNoteCodig("BL-ST-${status.ordinal}", order, client)
      note.status = status
      deliveryNoteCodigRepository.save(note)

      val found = deliveryNoteCodigRepository.findById(note.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun shipping_and_delivery_fields() {
    val client = createClient("CLI-DA07")
    val order = createOrderCodig("CA-DA-07", client)
    val note = DeliveryNoteCodig("BL-SHIP-01", order, client)
    note.shippingDate = LocalDate.of(2026, 3, 10)
    note.deliveryDate = LocalDate.of(2026, 3, 15)
    note.shippingAddress = "456 Shipping Ave"
    note.packageCount = 3
    note.signedBy = "Jean Dupont"
    note.signatureDate = LocalDate.of(2026, 3, 15)
    note.observations = "Handle with care"
    deliveryNoteCodigRepository.save(note)

    val found = deliveryNoteCodigRepository.findById(note.id!!).orElseThrow()
    assertThat(found.shippingDate).isEqualTo(LocalDate.of(2026, 3, 10))
    assertThat(found.deliveryDate).isEqualTo(LocalDate.of(2026, 3, 15))
    assertThat(found.shippingAddress).isEqualTo("456 Shipping Ave")
    assertThat(found.packageCount).isEqualTo(3)
    assertThat(found.signedBy).isEqualTo("Jean Dupont")
    assertThat(found.observations).isEqualTo("Handle with care")
  }
}
