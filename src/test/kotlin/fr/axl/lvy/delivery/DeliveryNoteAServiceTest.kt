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
class DeliveryNoteAServiceTest {

  @Autowired lateinit var deliveryNoteAService: DeliveryNoteAService
  @Autowired lateinit var deliveryNoteARepository: DeliveryNoteARepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  private fun createOrderA(number: String, client: Client): OrderA =
    orderARepository.save(OrderA(number, client, LocalDate.of(2026, 3, 1)))

  @Test
  fun save_generates_number_and_links_note_to_order() {
    val client = createClient("CLI-DAS-01")
    val order = createOrderA("CA-DAS-01", client)

    val note = DeliveryNoteA("", order, client)
    val saved = deliveryNoteAService.save(note)

    assertThat(saved.deliveryNoteNumber).startsWith("BL-")
    assertThat(saved.id).isNotNull
    val reloadedOrder = orderARepository.findById(order.id!!).orElseThrow()
    assertThat(reloadedOrder.deliveryNote!!.id).isEqualTo(saved.id)
  }

  @Test
  fun findByOrderAId_returns_existing_note() {
    val client = createClient("CLI-DAS-02")
    val order = createOrderA("CA-DAS-02", client)
    val note = deliveryNoteAService.save(DeliveryNoteA("", order, client))

    val found = deliveryNoteAService.findByOrderAId(order.id!!)

    assertThat(found).isNotNull
    assertThat(found!!.id).isEqualTo(note.id)
  }
}
