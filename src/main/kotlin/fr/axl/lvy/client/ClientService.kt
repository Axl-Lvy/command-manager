package fr.axl.lvy.client

import fr.axl.lvy.user.User
import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ClientService(private val clientRepository: ClientRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<Client> = clientRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findVisibleFor(company: User.Company): List<Client> = clientRepository.findVisibleFor(company)

  @Transactional(readOnly = true)
  fun findClientsVisibleFor(company: User.Company): List<Client> =
    clientRepository.findClientsVisibleFor(company)

  @Transactional(readOnly = true)
  fun findProducersVisibleFor(company: User.Company): List<Client> =
    clientRepository.findProducersVisibleFor(company)

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Client> = clientRepository.findById(id)

  @Transactional fun save(client: Client): Client = clientRepository.save(client)

  @Transactional
  fun delete(id: Long) {
    clientRepository.findById(id).ifPresent { it.softDelete() }
  }
}
