package fr.axl.lvy.client

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.user.User
import io.micrometer.core.instrument.MeterRegistry
import java.util.Locale
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

  /**
   * Returns the default supplier for CoDIG purchase orders — the own-company record representing
   * Netstone. Primary business rule: a client with role/type OWN_COMPANY named "Netstone".
   * Visibility NETSTONE is used only as a compatibility fallback.
   */
  @Transactional(readOnly = true)
  fun findDefaultCodigSupplier(): Optional<Client> {
    val ownCompanies =
      findByType(Client.ClientType.OWN_COMPANY).filter { it.role == Client.ClientRole.OWN_COMPANY }
    return Optional.ofNullable(
      ownCompanies.firstOrNull { it.name.lowercase(Locale.ROOT).contains("netstone") }
        ?: ownCompanies.firstOrNull { it.visibleCompany == User.Company.NETSTONE }
    )
  }

  /**
   * Returns the own-company record representing Codig itself. Primary business rule: an OWN_COMPANY
   * named "Codig". Visibility CODIG is used as a compatibility fallback.
   */
  @Transactional(readOnly = true)
  fun findDefaultCodigCompany(): Optional<Client> {
    val ownCompanies =
      findByType(Client.ClientType.OWN_COMPANY).filter { it.role == Client.ClientRole.OWN_COMPANY }
    return Optional.ofNullable(
      ownCompanies.firstOrNull { it.name.lowercase(Locale.ROOT).contains("codig") }
        ?: ownCompanies.firstOrNull { it.visibleCompany == User.Company.CODIG }
    )
  }

  @Transactional(readOnly = true)
  fun findVisibleFor(company: User.Company): List<Client> = clientRepository.findVisibleFor(company)

  @Transactional(readOnly = true)
  fun findClientsVisibleFor(company: User.Company): List<Client> =
    clientRepository.findClientsVisibleFor(company)

  @Transactional(readOnly = true)
  fun findProducersVisibleFor(company: User.Company): List<Client> =
    clientRepository.findProducersVisibleFor(company)

  /** Clients that can act as a buyer. */
  @Transactional(readOnly = true) fun findClients(): List<Client> = clientRepository.findClients()

  /** Clients that can be set as product supplier (producers + own companies). */
  @Transactional(readOnly = true)
  fun findSuppliersForProduct(): List<Client> = clientRepository.findSuppliersForProduct()

  @Transactional(readOnly = true)
  fun findByRole(role: Client.ClientRole): List<Client> = clientRepository.findByRole(role)

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
