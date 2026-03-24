package fr.axl.lvy.client;

import fr.axl.lvy.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

  private final ClientRepository clientRepository;

  ClientService(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  @Transactional(readOnly = true)
  public List<Client> findAll() {
    return clientRepository.findByDeletedAtIsNull();
  }

  @Transactional(readOnly = true)
  public List<Client> findVisibleFor(User.Company company) {
    return clientRepository.findVisibleFor(company);
  }

  @Transactional(readOnly = true)
  public List<Client> findClientsVisibleFor(User.Company company) {
    return clientRepository.findClientsVisibleFor(company);
  }

  @Transactional(readOnly = true)
  public List<Client> findProducersVisibleFor(User.Company company) {
    return clientRepository.findProducersVisibleFor(company);
  }

  @Transactional(readOnly = true)
  public Optional<Client> findById(Long id) {
    return clientRepository.findById(id);
  }

  @Transactional
  public Client save(Client client) {
    return clientRepository.save(client);
  }

  @Transactional
  public void delete(Long id) {
    clientRepository.findById(id).ifPresent(Client::softDelete);
  }
}
