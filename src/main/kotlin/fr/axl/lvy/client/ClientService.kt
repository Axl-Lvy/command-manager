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

  @Transactional(readOnly = true)
  fun findDetailedById(id: Long): Optional<Client> =
    Optional.ofNullable(clientRepository.findDetailedById(id))

  @Transactional
  fun save(client: Client): Client {
    if (client.clientCode.isBlank()) {
      client.clientCode = generateNextClientCode()
    }
    return clientRepository.save(client)
  }

  @Transactional
  fun delete(id: Long) {
    clientRepository.findById(id).ifPresent { it.softDelete() }
  }

  private fun generateNextClientCode(): String {
    val nextNumber =
      clientRepository
        .findAllClientCodes()
        .mapNotNull { code ->
          CLIENT_CODE_REGEX.matchEntire(code)?.groupValues?.get(1)?.toIntOrNull()
        }
        .maxOrNull()
        ?.plus(1) ?: 1
    return CLIENT_CODE_PREFIX + nextNumber.toString().padStart(6, '0')
  }

  companion object {
    private const val CLIENT_CODE_PREFIX = "C"
    private val CLIENT_CODE_REGEX = Regex("""C(\d{6})""")
  }
}
