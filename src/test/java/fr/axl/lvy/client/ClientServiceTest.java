package fr.axl.lvy.client;

import static org.assertj.core.api.Assertions.assertThat;

import fr.axl.lvy.client.contact.Contact;
import fr.axl.lvy.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ClientServiceTest {

  @Autowired ClientService clientService;
  @Autowired ClientRepository clientRepository;

  @Test
  void save_and_retrieve_client() {
    var client = new Client("CLI-001", "Acme Corp");
    client.setEmail("contact@acme.com");
    client.setPhone("0123456789");
    clientService.save(client);

    var found = clientService.findById(client.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Acme Corp");
    assertThat(found.get().getClientCode()).isEqualTo("CLI-001");
  }

  @Test
  void soft_delete_excludes_from_findAll() {
    var client = new Client("CLI-DEL", "To Delete");
    clientService.save(client);
    assertThat(clientService.findAll()).anyMatch(c -> c.getClientCode().equals("CLI-DEL"));

    clientService.delete(client.getId());
    clientRepository.flush();

    assertThat(clientService.findAll()).noneMatch(c -> c.getClientCode().equals("CLI-DEL"));
  }

  @Test
  void findVisibleFor_filters_by_company() {
    var clientA = new Client("CLI-A", "Company A Client");
    clientA.setVisibleCompany(User.Company.A);
    clientService.save(clientA);

    var clientB = new Client("CLI-B", "Company B Client");
    clientB.setVisibleCompany(User.Company.B);
    clientService.save(clientB);

    var clientAB = new Client("CLI-AB", "Both Companies Client");
    clientAB.setVisibleCompany(User.Company.AB);
    clientService.save(clientAB);

    var visibleForA = clientService.findVisibleFor(User.Company.A);
    assertThat(visibleForA).anyMatch(c -> c.getClientCode().equals("CLI-A"));
    assertThat(visibleForA).noneMatch(c -> c.getClientCode().equals("CLI-B"));
    assertThat(visibleForA).anyMatch(c -> c.getClientCode().equals("CLI-AB"));

    var visibleForB = clientService.findVisibleFor(User.Company.B);
    assertThat(visibleForB).noneMatch(c -> c.getClientCode().equals("CLI-A"));
    assertThat(visibleForB).anyMatch(c -> c.getClientCode().equals("CLI-B"));
    assertThat(visibleForB).anyMatch(c -> c.getClientCode().equals("CLI-AB"));
  }

  @Test
  void findClientsVisibleFor_filters_by_role_and_company() {
    var clientOnly = new Client("CLI-C", "Client Only");
    clientOnly.setRole(Client.ClientRole.CLIENT);
    clientOnly.setVisibleCompany(User.Company.A);
    clientService.save(clientOnly);

    var producerOnly = new Client("CLI-P", "Producer Only");
    producerOnly.setRole(Client.ClientRole.PRODUCER);
    producerOnly.setVisibleCompany(User.Company.A);
    clientService.save(producerOnly);

    var both = new Client("CLI-BOTH", "Client And Producer");
    both.setRole(Client.ClientRole.BOTH);
    both.setVisibleCompany(User.Company.A);
    clientService.save(both);

    var clients = clientService.findClientsVisibleFor(User.Company.A);
    assertThat(clients).anyMatch(c -> c.getClientCode().equals("CLI-C"));
    assertThat(clients).noneMatch(c -> c.getClientCode().equals("CLI-P"));
    assertThat(clients).anyMatch(c -> c.getClientCode().equals("CLI-BOTH"));

    var producers = clientService.findProducersVisibleFor(User.Company.A);
    assertThat(producers).noneMatch(c -> c.getClientCode().equals("CLI-C"));
    assertThat(producers).anyMatch(c -> c.getClientCode().equals("CLI-P"));
    assertThat(producers).anyMatch(c -> c.getClientCode().equals("CLI-BOTH"));
  }

  @Test
  void isClient_and_isProducer_reflect_role() {
    var client = new Client("CLI-R1", "Client Role");
    client.setRole(Client.ClientRole.CLIENT);
    assertThat(client.isClient()).isTrue();
    assertThat(client.isProducer()).isFalse();

    var producer = new Client("CLI-R2", "Producer Role");
    producer.setRole(Client.ClientRole.PRODUCER);
    assertThat(producer.isClient()).isFalse();
    assertThat(producer.isProducer()).isTrue();

    var both = new Client("CLI-R3", "Both Role");
    both.setRole(Client.ClientRole.BOTH);
    assertThat(both.isClient()).isTrue();
    assertThat(both.isProducer()).isTrue();
  }

  @Test
  void client_cascades_contacts() {
    var client = new Client("CLI-CON", "With Contacts");
    var contact = new Contact(client, "Dupont");
    contact.setFirstName("Jean");
    contact.setRole(Contact.ContactRole.PRIMARY);
    client.getContacts().add(contact);
    clientService.save(client);
    clientRepository.flush();

    var found = clientService.findById(client.getId()).orElseThrow();
    assertThat(found.getContacts()).hasSize(1);
    assertThat(found.getContacts().get(0).getLastName()).isEqualTo("Dupont");
    assertThat(found.getContacts().get(0).getFirstName()).isEqualTo("Jean");
  }

  @Test
  void client_defaults_are_correct() {
    var client = new Client("CLI-DEF", "Defaults");
    assertThat(client.getType()).isEqualTo(Client.ClientType.COMPANY);
    assertThat(client.getRole()).isEqualTo(Client.ClientRole.CLIENT);
    assertThat(client.getStatus()).isEqualTo(Client.Status.ACTIVE);
    assertThat(client.getDefaultDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
