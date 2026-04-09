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
class DeliveryNoteCodigServiceTest {

  @Autowired lateinit var deliveryNoteCodigService: DeliveryNoteCodigService
  @Autowired lateinit var deliveryNoteCodigRepository: DeliveryNoteCodigRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  private fun createOrderCodig(number: String, client: Client): OrderCodig =
    orderCodigRepository.save(OrderCodig(number, client, LocalDate.of(2026, 3, 1)))

  @Test
  fun save_generates_number_and_links_note_to_order() {
    val client = createClient("CLI-DAS-01")
    val order = createOrderCodig("CA-DAS-01", client)

    val note = DeliveryNoteCodig("", order, client)
    val saved = deliveryNoteCodigService.save(note)

    assertThat(saved.deliveryNoteNumber).startsWith("BL-")
    assertThat(saved.id).isNotNull
    val reloadedOrder = orderCodigRepository.findById(order.id!!).orElseThrow()
    assertThat(reloadedOrder.deliveryNote!!.id).isEqualTo(saved.id)
  }

  @Test
  fun findByOrderCodigId_returns_existing_note() {
    val client = createClient("CLI-DAS-02")
    val order = createOrderCodig("CA-DAS-02", client)
    val note = deliveryNoteCodigService.save(DeliveryNoteCodig("", order, client))

    val found = deliveryNoteCodigService.findByOrderCodigId(order.id!!)

    assertThat(found).isNotNull
    assertThat(found!!.id).isEqualTo(note.id)
  }
}
