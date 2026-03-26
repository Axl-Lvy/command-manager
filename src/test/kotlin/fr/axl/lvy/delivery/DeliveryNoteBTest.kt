package fr.axl.lvy.delivery

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class DeliveryNoteBTest {

  @Autowired lateinit var deliveryNoteBRepository: DeliveryNoteBRepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var orderBRepository: OrderBRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createOrderB(number: String): OrderB {
    val client = clientRepository.save(Client("CLI-DB-$number", "Client"))
    val orderA = orderARepository.save(OrderA("CA-DB-$number", client, LocalDate.of(2026, 3, 1)))
    return orderBRepository.save(OrderB(number, orderA))
  }

  @Test
  fun save_and_retrieve_delivery_note() {
    val orderB = createOrderB("CB-DB-01")
    val note = DeliveryNoteB("BLB-2026-0001", orderB)
    note.containerNumber = "CONT-001"
    note.lot = "LOT-A"
    note.seals = "SEAL-123"
    deliveryNoteBRepository.save(note)

    val found = deliveryNoteBRepository.findById(note.id!!)
    assertThat(found).isPresent
    assertThat(found.get().deliveryNoteNumber).isEqualTo("BLB-2026-0001")
    assertThat(found.get().containerNumber).isEqualTo("CONT-001")
    assertThat(found.get().lot).isEqualTo("LOT-A")
    assertThat(found.get().seals).isEqualTo("SEAL-123")
  }

  @Test
  fun defaults_are_correct() {
    val orderB = createOrderB("CB-DB-02")
    val note = DeliveryNoteB("BLB-DEF-01", orderB)

    assertThat(note.status).isEqualTo(DeliveryNoteB.DeliveryNoteBStatus.IN_TRANSIT)
    assertThat(note.shippingDate).isNull()
    assertThat(note.arrivalDate).isNull()
    assertThat(note.containerNumber).isNull()
    assertThat(note.pdfPath).isNull()
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val orderB = createOrderB("CB-DB-03")
    val note = DeliveryNoteB("BLB-DEL-01", orderB)
    deliveryNoteBRepository.save(note)

    assertThat(deliveryNoteBRepository.findByDeletedAtIsNull()).anyMatch {
      it.deliveryNoteNumber == "BLB-DEL-01"
    }

    note.softDelete()
    deliveryNoteBRepository.saveAndFlush(note)

    assertThat(deliveryNoteBRepository.findByDeletedAtIsNull()).noneMatch {
      it.deliveryNoteNumber == "BLB-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val orderB = createOrderB("CB-DB-04")
    val note = DeliveryNoteB("BLB-REST-01", orderB)

    note.softDelete()
    assertThat(note.isDeleted()).isTrue

    note.restore()
    assertThat(note.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val orderB = createOrderB("CB-DB-05")
    val note = DeliveryNoteB("BLB-TS-01", orderB)
    deliveryNoteBRepository.save(note)

    assertThat(note.createdAt).isNotNull
    assertThat(note.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val orderB = createOrderB("CB-DB-06")
    for (status in DeliveryNoteB.DeliveryNoteBStatus.entries) {
      val note = DeliveryNoteB("BLB-ST-${status.ordinal}", orderB)
      note.status = status
      deliveryNoteBRepository.save(note)

      val found = deliveryNoteBRepository.findById(note.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun shipping_and_arrival_fields() {
    val orderB = createOrderB("CB-DB-07")
    val note = DeliveryNoteB("BLB-SHIP-01", orderB)
    note.shippingDate = LocalDate.of(2026, 3, 1)
    note.arrivalDate = LocalDate.of(2026, 3, 20)
    note.pdfPath = "/documents/blb-001.pdf"
    note.observations = "Fragile cargo"
    deliveryNoteBRepository.save(note)

    val found = deliveryNoteBRepository.findById(note.id!!).orElseThrow()
    assertThat(found.shippingDate).isEqualTo(LocalDate.of(2026, 3, 1))
    assertThat(found.arrivalDate).isEqualTo(LocalDate.of(2026, 3, 20))
    assertThat(found.pdfPath).isEqualTo("/documents/blb-001.pdf")
    assertThat(found.observations).isEqualTo("Fragile cargo")
  }
}
