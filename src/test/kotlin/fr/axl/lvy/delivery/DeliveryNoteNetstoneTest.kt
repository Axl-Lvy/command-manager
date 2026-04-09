package fr.axl.lvy.delivery

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class DeliveryNoteNetstoneTest {

  @Autowired lateinit var deliveryNoteNetstoneRepository: DeliveryNoteNetstoneRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createOrderNetstone(number: String): OrderNetstone {
    val client = clientRepository.save(Client("CLI-DB-$number", "Client"))
    val orderCodig =
      orderCodigRepository.save(OrderCodig("CA-DB-$number", client, LocalDate.of(2026, 3, 1)))
    return orderNetstoneRepository.save(OrderNetstone(number, orderCodig))
  }

  @Test
  fun save_and_retrieve_delivery_note() {
    val orderNetstone = createOrderNetstone("CB-DB-01")
    val note = DeliveryNoteNetstone("BLB-2026-0001", orderNetstone)
    note.containerNumber = "CONT-001"
    note.lot = "LOT-A"
    note.seals = "SEAL-123"
    deliveryNoteNetstoneRepository.save(note)

    val found = deliveryNoteNetstoneRepository.findById(note.id!!)
    assertThat(found).isPresent
    assertThat(found.get().deliveryNoteNumber).isEqualTo("BLB-2026-0001")
    assertThat(found.get().containerNumber).isEqualTo("CONT-001")
    assertThat(found.get().lot).isEqualTo("LOT-A")
    assertThat(found.get().seals).isEqualTo("SEAL-123")
  }

  @Test
  fun defaults_are_correct() {
    val orderNetstone = createOrderNetstone("CB-DB-02")
    val note = DeliveryNoteNetstone("BLB-DEF-01", orderNetstone)

    assertThat(note.status).isEqualTo(DeliveryNoteNetstone.DeliveryNoteNetstoneStatus.IN_TRANSIT)
    assertThat(note.shippingDate).isNull()
    assertThat(note.arrivalDate).isNull()
    assertThat(note.containerNumber).isNull()
    assertThat(note.pdfPath).isNull()
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val orderNetstone = createOrderNetstone("CB-DB-03")
    val note = DeliveryNoteNetstone("BLB-DEL-01", orderNetstone)
    deliveryNoteNetstoneRepository.save(note)

    assertThat(deliveryNoteNetstoneRepository.findByDeletedAtIsNull()).anyMatch {
      it.deliveryNoteNumber == "BLB-DEL-01"
    }

    note.softDelete()
    deliveryNoteNetstoneRepository.saveAndFlush(note)

    assertThat(deliveryNoteNetstoneRepository.findByDeletedAtIsNull()).noneMatch {
      it.deliveryNoteNumber == "BLB-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val orderNetstone = createOrderNetstone("CB-DB-04")
    val note = DeliveryNoteNetstone("BLB-REST-01", orderNetstone)

    note.softDelete()
    assertThat(note.isDeleted()).isTrue

    note.restore()
    assertThat(note.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val orderNetstone = createOrderNetstone("CB-DB-05")
    val note = DeliveryNoteNetstone("BLB-TS-01", orderNetstone)
    deliveryNoteNetstoneRepository.save(note)

    assertThat(note.createdAt).isNotNull
    assertThat(note.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val orderNetstone = createOrderNetstone("CB-DB-06")
    for (status in DeliveryNoteNetstone.DeliveryNoteNetstoneStatus.entries) {
      val note = DeliveryNoteNetstone("BLB-ST-${status.ordinal}", orderNetstone)
      note.status = status
      deliveryNoteNetstoneRepository.save(note)

      val found = deliveryNoteNetstoneRepository.findById(note.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun shipping_and_arrival_fields() {
    val orderNetstone = createOrderNetstone("CB-DB-07")
    val note = DeliveryNoteNetstone("BLB-SHIP-01", orderNetstone)
    note.shippingDate = LocalDate.of(2026, 3, 1)
    note.arrivalDate = LocalDate.of(2026, 3, 20)
    note.pdfPath = "/documents/blb-001.pdf"
    note.observations = "Fragile cargo"
    deliveryNoteNetstoneRepository.save(note)

    val found = deliveryNoteNetstoneRepository.findById(note.id!!).orElseThrow()
    assertThat(found.shippingDate).isEqualTo(LocalDate.of(2026, 3, 1))
    assertThat(found.arrivalDate).isEqualTo(LocalDate.of(2026, 3, 20))
    assertThat(found.pdfPath).isEqualTo("/documents/blb-001.pdf")
    assertThat(found.observations).isEqualTo("Fragile cargo")
  }
}
