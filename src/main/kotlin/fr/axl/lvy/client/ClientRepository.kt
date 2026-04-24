package fr.axl.lvy.client

import fr.axl.lvy.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ClientRepository : JpaRepository<Client, Long> {

  fun findByDeletedAtIsNull(): List<Client>

  /** Paginated fetch excluding own-company entries (used by the customer/supplier list view). */
  fun findByDeletedAtIsNullAndTypeNot(type: Client.ClientType, pageable: Pageable): Page<Client>

  fun findByDeletedAtIsNullAndTypeOrderByNameAsc(type: Client.ClientType): List<Client>

  fun findByDeletedAtIsNullAndTypeOrderByNameAsc(
    type: Client.ClientType,
    pageable: Pageable,
  ): Page<Client>

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

  /** All non-deleted clients that can act as a buyer (role CLIENT or BOTH). */
  @Query("SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('CLIENT', 'BOTH')")
  fun findClients(): List<Client>

  /**
   * All non-deleted clients usable as a product supplier: producers, both-role clients, and
   * own-company entries (for inter-company flows).
   */
  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('PRODUCER', 'BOTH', 'OWN_COMPANY')"
  )
  fun findSuppliersForProduct(): List<Client>

  /** All non-deleted clients with the given role. */
  @Query("SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role = :role")
  fun findByRole(role: Client.ClientRole): List<Client>
}
