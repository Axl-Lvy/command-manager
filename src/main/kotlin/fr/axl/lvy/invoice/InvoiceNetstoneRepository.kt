package fr.axl.lvy.invoice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface InvoiceNetstoneRepository : JpaRepository<InvoiceNetstone, Long> {
  @Query(
    """
      SELECT DISTINCT i FROM InvoiceNetstone i
      LEFT JOIN FETCH i.recipient
      LEFT JOIN FETCH i.orderNetstone
      LEFT JOIN FETCH i.verifiedBy
      WHERE i.deletedAt IS NULL
    """
  )
  fun findByDeletedAtIsNull(): List<InvoiceNetstone>

  @Query(
    """
      SELECT i FROM InvoiceNetstone i
      LEFT JOIN FETCH i.recipient
      LEFT JOIN FETCH i.orderNetstone o
      LEFT JOIN FETCH o.orderCodig
      LEFT JOIN FETCH i.verifiedBy
      WHERE i.id = :id
    """
  )
  fun findDetailedById(id: Long): InvoiceNetstone?

  @Query(
    """
      SELECT i FROM InvoiceNetstone i
      LEFT JOIN FETCH i.recipient
      LEFT JOIN FETCH i.orderNetstone o
      LEFT JOIN FETCH o.orderCodig
      LEFT JOIN FETCH i.verifiedBy
      WHERE i.orderNetstone.id = :orderNetstoneId
        AND i.deletedAt IS NULL
      ORDER BY i.id DESC
    """
  )
  fun findDetailedByOrderNetstoneIdOrdered(orderNetstoneId: Long): List<InvoiceNetstone>

  fun findDetailedByOrderNetstoneId(orderNetstoneId: Long): InvoiceNetstone? =
    findDetailedByOrderNetstoneIdOrdered(orderNetstoneId).firstOrNull()
}
