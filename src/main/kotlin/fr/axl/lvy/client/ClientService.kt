package fr.axl.lvy.client

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.user.User
import io.micrometer.core.instrument.MeterRegistry
import java.util.Optional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages client lifecycle. Automatically assigns a unique client code on first save. */
@Service
class ClientService(
  private val clientRepository: ClientRepository,
  private val numberSequenceService: NumberSequenceService,
  private val meterRegistry: MeterRegistry,
) {
  private val clientsCreatedCounter = meterRegistry.counter("client")

  companion object {
    private val log = LoggerFactory.getLogger(ClientService::class.java)
    private const val MAX_SEQUENCE_RETRIES = 100
  }

  @Transactional(readOnly = true)
  fun findAll(): List<Client> = clientRepository.findByDeletedAtIsNull()

  @Transactional(readOnly = true)
  fun findByType(type: Client.ClientType): List<Client> =
    clientRepository.findByDeletedAtIsNullAndTypeOrderByNameAsc(type)

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
    val isNew = client.clientCode.isBlank()
    if (isNew) {
      client.clientCode = generateNextClientCode()
    }
    val saved = clientRepository.save(client)
    if (isNew) {
      clientsCreatedCounter.increment()
      log.info("Client created: code={} name={}", saved.clientCode, saved.name)
    }
    return saved
  }

  @Transactional
  fun delete(id: Long) {
    clientRepository.findById(id).ifPresent { it.softDelete() }
  }

  private fun generateNextClientCode(): String = generateSequenceValue { code ->
    clientRepository.existsByClientCode(code)
  }

  private fun generateSequenceValue(exists: (String) -> Boolean): String {
    repeat(MAX_SEQUENCE_RETRIES) {
      val nextCode = numberSequenceService.nextNumber(NumberSequenceService.CLIENT)
      if (!exists(nextCode)) return nextCode
    }
    throw IllegalStateException(
      "Impossible de générer un code client unique après $MAX_SEQUENCE_RETRIES tentatives"
    )
  }
}
