package fr.axl.lvy.client

import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
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
  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var incotermService: IncotermService

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
  fun save_generates_next_available_client_code_when_sequence_is_behind() {
    clientService.save(Client("C000001", "Existing Client"))
    val client = Client(name = "Generated Client")

    clientService.save(client)

    assertThat(client.clientCode).isEqualTo("C000002")
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
    clientA.visibleCompany = User.Company.CODIG
    clientService.save(clientA)

    val clientB = Client("CLI-B", "Company B Client")
    clientB.visibleCompany = User.Company.NETSTONE
    clientService.save(clientB)

    val clientAB = Client("CLI-AB", "Both Companies Client")
    clientAB.visibleCompany = User.Company.BOTH
    clientService.save(clientAB)

    val visibleForA = clientService.findVisibleFor(User.Company.CODIG)
    assertThat(visibleForA).anyMatch { it.clientCode == "CLI-A" }
    assertThat(visibleForA).noneMatch { it.clientCode == "CLI-B" }
    assertThat(visibleForA).anyMatch { it.clientCode == "CLI-AB" }

    val visibleForB = clientService.findVisibleFor(User.Company.NETSTONE)
    assertThat(visibleForB).noneMatch { it.clientCode == "CLI-A" }
    assertThat(visibleForB).anyMatch { it.clientCode == "CLI-B" }
    assertThat(visibleForB).anyMatch { it.clientCode == "CLI-AB" }
  }

  @Test
  fun findClientsVisibleFor_filters_by_role_and_company() {
    val clientOnly = Client("CLI-C", "Client Only")
    clientOnly.role = Client.ClientRole.CLIENT
    clientOnly.visibleCompany = User.Company.CODIG
    clientService.save(clientOnly)

    val producerOnly = Client("CLI-P", "Producer Only")
    producerOnly.role = Client.ClientRole.PRODUCER
    producerOnly.visibleCompany = User.Company.CODIG
    clientService.save(producerOnly)

    val both = Client("CLI-BOTH", "Client And Producer")
    both.role = Client.ClientRole.BOTH
    both.visibleCompany = User.Company.CODIG
    clientService.save(both)

    val clients = clientService.findClientsVisibleFor(User.Company.CODIG)
    assertThat(clients).anyMatch { it.clientCode == "CLI-C" }
    assertThat(clients).noneMatch { it.clientCode == "CLI-P" }
    assertThat(clients).anyMatch { it.clientCode == "CLI-BOTH" }

    val producers = clientService.findProducersVisibleFor(User.Company.CODIG)
    assertThat(producers).noneMatch { it.clientCode == "CLI-C" }
    assertThat(producers).anyMatch { it.clientCode == "CLI-P" }
    assertThat(producers).anyMatch { it.clientCode == "CLI-BOTH" }
  }

  @Test
  fun findByType_filters_own_companies() {
    val ownCompany = Client("CLI-OWN", "My Company A")
    ownCompany.type = Client.ClientType.OWN_COMPANY
    clientService.save(ownCompany)

    val regularClient = Client("CLI-REG", "Regular Client")
    regularClient.type = Client.ClientType.COMPANY
    clientService.save(regularClient)

    val ownCompanies = clientService.findByType(Client.ClientType.OWN_COMPANY)

    assertThat(ownCompanies).anyMatch { it.clientCode == "CLI-OWN" }
    assertThat(ownCompanies).noneMatch { it.clientCode == "CLI-REG" }
  }

  @Test
  fun findDefaultCodigSupplier_prefers_netstone_own_company() {
    val codig = Client("CLI-OWN-A", "Société A")
    codig.type = Client.ClientType.OWN_COMPANY
    codig.role = Client.ClientRole.OWN_COMPANY
    codig.visibleCompany = User.Company.CODIG
    clientService.save(codig)

    val netstone = Client("CLI-OWN-B", "Netstone")
    netstone.type = Client.ClientType.OWN_COMPANY
    netstone.role = Client.ClientRole.OWN_COMPANY
    netstone.visibleCompany = User.Company.NETSTONE
    clientService.save(netstone)

    val defaultSupplier = clientService.findDefaultCodigSupplier()

    assertThat(defaultSupplier).isPresent
    assertThat(defaultSupplier.get().id).isEqualTo(netstone.id)
  }

  @Test
  fun findDefaultCodigSupplier_does_not_fallback_to_codig_own_company() {
    val codig = Client("CLI-OWN-CODIG", "Société A")
    codig.type = Client.ClientType.OWN_COMPANY
    codig.role = Client.ClientRole.OWN_COMPANY
    codig.visibleCompany = User.Company.CODIG
    clientService.save(codig)

    val defaultSupplier = clientService.findDefaultCodigSupplier()

    assertThat(defaultSupplier).isEmpty
  }

  @Test
  fun findDefaultCodigSupplier_finds_netstone_by_own_company_role() {
    val netstone = Client("CLI-OWN-NET", "Netstone")
    netstone.type = Client.ClientType.OWN_COMPANY
    netstone.role = Client.ClientRole.OWN_COMPANY
    netstone.visibleCompany = User.Company.BOTH
    clientService.save(netstone)

    val defaultSupplier = clientService.findDefaultCodigSupplier()

    assertThat(defaultSupplier).isPresent
    assertThat(defaultSupplier.get().id).isEqualTo(netstone.id)
  }

  @Test
  fun findDefaultCodigCompany_prefers_codig_own_company() {
    val codig = Client("CLI-OWN-COD", "Codig")
    codig.type = Client.ClientType.OWN_COMPANY
    codig.role = Client.ClientRole.OWN_COMPANY
    codig.visibleCompany = User.Company.CODIG
    clientService.save(codig)

    val netstone = Client("CLI-OWN-NST", "Netstone")
    netstone.type = Client.ClientType.OWN_COMPANY
    netstone.role = Client.ClientRole.OWN_COMPANY
    netstone.visibleCompany = User.Company.NETSTONE
    clientService.save(netstone)

    val defaultCompany = clientService.findDefaultCodigCompany()

    assertThat(defaultCompany).isPresent
    assertThat(defaultCompany.get().id).isEqualTo(codig.id)
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

    val ownCompany = Client("CLI-R4", "Own Company")
    ownCompany.role = Client.ClientRole.OWN_COMPANY
    assertThat(ownCompany.isClient()).isFalse
    assertThat(ownCompany.isProducer()).isFalse
    assertThat(ownCompany.isSupplierForProduct()).isTrue
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
    assertThat(client.visibleCompany).isEqualTo(User.Company.BOTH)
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

  @Test
  fun findDetailedById_returns_default_delivery_terms() {
    val incoterm = incotermService.save(Incoterm("DAP-CLIENT", "Delivered at place client"))
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Export test"))
    val client = Client("CLI-DET-02", "Delivery Terms Client")
    client.fiscalPosition = fiscalPosition
    client.incoterm = incoterm
    client.incotermLocation = "Paris"
    client.deliveryPort = "Le Havre"
    clientService.save(client)

    val found = clientService.findDetailedById(client.id!!)

    assertThat(found).isPresent
    assertThat(found.get().fiscalPosition?.position).isEqualTo("Export test")
    assertThat(found.get().incoterm?.name).isEqualTo("DAP-CLIENT")
    assertThat(found.get().incotermLocation).isEqualTo("Paris")
    assertThat(found.get().deliveryPort).isEqualTo("Le Havre")
  }

  @Test
  fun findDetailedById_returns_delivery_addresses() {
    val client = Client("CLI-DET-03", "Delivery Address Client")
    client.deliveryAddresses.add(
      ClientDeliveryAddress(client, "Entrepot Lyon", "12 rue de Lyon\n69000 Lyon").apply {
        defaultAddress = true
      }
    )
    client.deliveryAddresses.add(
      ClientDeliveryAddress(client, "Port Marseille", "Quai 4\n13000 Marseille")
    )
    clientService.save(client)

    val found = clientService.findDetailedById(client.id!!)

    assertThat(found).isPresent
    assertThat(found.get().deliveryAddresses).hasSize(2)
    assertThat(found.get().deliveryAddresses.first { it.defaultAddress }.label)
      .isEqualTo("Entrepot Lyon")
  }
}
