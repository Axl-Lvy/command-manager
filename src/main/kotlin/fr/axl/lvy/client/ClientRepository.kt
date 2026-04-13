package fr.axl.lvy.client

import fr.axl.lvy.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ClientRepository : JpaRepository<Client, Long> {

  fun findByDeletedAtIsNull(): List<Client>

  fun findByDeletedAtIsNullAndTypeOrderByNameAsc(type: Client.ClientType): List<Client>

  fun existsByClientCode(clientCode: String): Boolean

  /** Eagerly fetches contacts and reference data for the detail/edit form. */
  @Query(
    """
      SELECT DISTINCT c
      FROM Client c
      LEFT JOIN FETCH c.contacts
      LEFT JOIN FETCH c.deliveryAddresses
      LEFT JOIN FETCH c.paymentTerm
      LEFT JOIN FETCH c.fiscalPosition
      LEFT JOIN FETCH c.incoterm
      WHERE c.id = :id
    """
  )
  fun findDetailedById(id: Long): Client?

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND (c.visibleCompany = :company OR c.visibleCompany = 'BOTH')"
  )
  fun findVisibleFor(company: User.Company): List<Client>

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('CLIENT', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'BOTH')"
  )
  fun findClientsVisibleFor(company: User.Company): List<Client>

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('PRODUCER', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'BOTH')"
  )
  fun findProducersVisibleFor(company: User.Company): List<Client>
}
