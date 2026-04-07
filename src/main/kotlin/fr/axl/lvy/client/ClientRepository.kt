package fr.axl.lvy.client

import fr.axl.lvy.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ClientRepository : JpaRepository<Client, Long> {

  fun findByDeletedAtIsNull(): List<Client>

  @Query(
    """
      SELECT DISTINCT c
      FROM Client c
      LEFT JOIN FETCH c.contacts
      WHERE c.id = :id
    """
  )
  fun findDetailedById(id: Long): Client?

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')"
  )
  fun findVisibleFor(company: User.Company): List<Client>

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('CLIENT', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')"
  )
  fun findClientsVisibleFor(company: User.Company): List<Client>

  @Query(
    "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('PRODUCER', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')"
  )
  fun findProducersVisibleFor(company: User.Company): List<Client>
}
