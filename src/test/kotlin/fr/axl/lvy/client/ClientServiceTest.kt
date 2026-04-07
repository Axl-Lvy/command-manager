package fr.axl.lvy.client

import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.user.User
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ClientServiceTest {

  @Autowired lateinit var clientService: ClientService
  @Autowired lateinit var clientRepository: ClientRepository

  @Test
  fun save_and_retrieve_client() {
    val client = Client(name = "Acme Corp")
    client.email = "contact@acme.com"
    client.phone = "0123456789"
    clientService.save(client)

    val found = clientService.findById(client.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Acme Corp")
    assertThat(found.get().clientCode).matches("""C\d{6}""")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = Client("CLI-DEL", "To Delete")
    clientService.save(client)
    assertThat(clientService.findAll()).anyMatch { it.clientCode == "CLI-DEL" }

    clientService.delete(client.id!!)
    clientRepository.flush()

    assertThat(clientService.findAll()).noneMatch { it.clientCode == "CLI-DEL" }
  }

  @Test
  fun findVisibleFor_filters_by_company() {
    val clientA = Client("CLI-A", "Company A Client")
    clientA.visibleCompany = User.Company.A
    clientService.save(clientA)

    val clientB = Client("CLI-B", "Company B Client")
    clientB.visibleCompany = User.Company.B
    clientService.save(clientB)

    val clientAB = Client("CLI-AB", "Both Companies Client")
    clientAB.visibleCompany = User.Company.AB
    clientService.save(clientAB)

    val visibleForA = clientService.findVisibleFor(User.Company.A)
    assertThat(visibleForA).anyMatch { it.clientCode == "CLI-A" }
    assertThat(visibleForA).noneMatch { it.clientCode == "CLI-B" }
    assertThat(visibleForA).anyMatch { it.clientCode == "CLI-AB" }

    val visibleForB = clientService.findVisibleFor(User.Company.B)
    assertThat(visibleForB).noneMatch { it.clientCode == "CLI-A" }
    assertThat(visibleForB).anyMatch { it.clientCode == "CLI-B" }
    assertThat(visibleForB).anyMatch { it.clientCode == "CLI-AB" }
  }

  @Test
  fun findClientsVisibleFor_filters_by_role_and_company() {
    val clientOnly = Client("CLI-C", "Client Only")
    clientOnly.role = Client.ClientRole.CLIENT
    clientOnly.visibleCompany = User.Company.A
    clientService.save(clientOnly)

    val producerOnly = Client("CLI-P", "Producer Only")
    producerOnly.role = Client.ClientRole.PRODUCER
    producerOnly.visibleCompany = User.Company.A
    clientService.save(producerOnly)

    val both = Client("CLI-BOTH", "Client And Producer")
    both.role = Client.ClientRole.BOTH
    both.visibleCompany = User.Company.A
    clientService.save(both)

    val clients = clientService.findClientsVisibleFor(User.Company.A)
    assertThat(clients).anyMatch { it.clientCode == "CLI-C" }
    assertThat(clients).noneMatch { it.clientCode == "CLI-P" }
    assertThat(clients).anyMatch { it.clientCode == "CLI-BOTH" }

    val producers = clientService.findProducersVisibleFor(User.Company.A)
    assertThat(producers).noneMatch { it.clientCode == "CLI-C" }
    assertThat(producers).anyMatch { it.clientCode == "CLI-P" }
    assertThat(producers).anyMatch { it.clientCode == "CLI-BOTH" }
  }

  @Test
  fun isClient_and_isProducer_reflect_role() {
    val client = Client("CLI-R1", "Client Role")
    client.role = Client.ClientRole.CLIENT
    assertThat(client.isClient()).isTrue
    assertThat(client.isProducer()).isFalse

    val producer = Client("CLI-R2", "Producer Role")
    producer.role = Client.ClientRole.PRODUCER
    assertThat(producer.isClient()).isFalse
    assertThat(producer.isProducer()).isTrue

    val both = Client("CLI-R3", "Both Role")
    both.role = Client.ClientRole.BOTH
    assertThat(both.isClient()).isTrue
    assertThat(both.isProducer()).isTrue
  }

  @Test
  fun client_cascades_contacts() {
    val client = Client("CLI-CON", "With Contacts")
    val contact = Contact(client, "Dupont")
    contact.firstName = "Jean"
    contact.role = Contact.ContactRole.PRIMARY
    client.contacts.add(contact)
    clientService.save(client)
    clientRepository.flush()

    val found = clientService.findById(client.id!!).orElseThrow()
    assertThat(found.contacts).hasSize(1)
    assertThat(found.contacts[0].lastName).isEqualTo("Dupont")
    assertThat(found.contacts[0].firstName).isEqualTo("Jean")
  }

  @Test
  fun client_defaults_are_correct() {
    val client = Client("CLI-DEF", "Defaults")
    assertThat(client.type).isEqualTo(Client.ClientType.COMPANY)
    assertThat(client.role).isEqualTo(Client.ClientRole.CLIENT)
    assertThat(client.status).isEqualTo(Client.Status.ACTIVE)
    assertThat(client.defaultDiscount).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun findDetailedById_returns_client_with_contacts() {
    val client = Client("CLI-DET-01", "Detailed Client")
    clientService.save(client)

    val found = clientService.findDetailedById(client.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Detailed Client")
  }
}
