package fr.axl.lvy.client.contact

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ContactTest {

  @Autowired lateinit var contactRepository: ContactRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_contact() {
    val client = createClient("CLI-CT01")
    val contact = Contact(client, "Dupont")
    contact.firstName = "Jean"
    contact.email = "jean@example.com"
    contact.phone = "0123456789"
    contactRepository.save(contact)

    val found = contactRepository.findById(contact.id!!)
    assertThat(found).isPresent
    assertThat(found.get().lastName).isEqualTo("Dupont")
    assertThat(found.get().firstName).isEqualTo("Jean")
    assertThat(found.get().email).isEqualTo("jean@example.com")
  }

  @Test
  fun findByClientId_returns_contacts() {
    val client = createClient("CLI-CT02")
    val c1 = Contact(client, "Martin")
    val c2 = Contact(client, "Bernard")
    contactRepository.save(c1)
    contactRepository.save(c2)

    val contacts = contactRepository.findByClientId(client.id!!)
    assertThat(contacts).hasSize(2)
    assertThat(contacts.map { it.lastName }).containsExactlyInAnyOrder("Martin", "Bernard")
  }

  @Test
  fun findByClientId_returns_empty_for_other_client() {
    val client1 = createClient("CLI-CT03")
    val client2 = createClient("CLI-CT04")
    contactRepository.save(Contact(client1, "Only"))

    assertThat(contactRepository.findByClientId(client2.id!!)).isEmpty()
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-CT05")
    val contact = Contact(client, "Default")
    assertThat(contact.role).isEqualTo(Contact.ContactRole.OTHER)
    assertThat(contact.active).isTrue
    assertThat(contact.firstName).isNull()
    assertThat(contact.email).isNull()
    assertThat(contact.phone).isNull()
    assertThat(contact.mobile).isNull()
    assertThat(contact.jobTitle).isNull()
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-CT06")
    val contact = Contact(client, "Timed")
    contactRepository.save(contact)

    assertThat(contact.createdAt).isNotNull
    assertThat(contact.updatedAt).isNotNull
  }

  @Test
  fun contact_roles() {
    val client = createClient("CLI-CT07")

    for (role in Contact.ContactRole.entries) {
      val contact = Contact(client, "Contact-${role.name}")
      contact.role = role
      contactRepository.save(contact)

      val found = contactRepository.findById(contact.id!!).orElseThrow()
      assertThat(found.role).isEqualTo(role)
    }
  }
}
