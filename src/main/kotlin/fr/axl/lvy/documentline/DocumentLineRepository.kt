package fr.axl.lvy.documentline

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DocumentLineRepository : JpaRepository<DocumentLine, Long> {
  fun findByDocumentTypeAndDocumentIdOrderByPosition(
    documentType: DocumentLine.DocumentType,
    documentId: Long,
  ): List<DocumentLine>

  /**
   * Same as [findByDocumentTypeAndDocumentIdOrderByPosition] but eagerly fetches the product to
   * avoid N+1 queries when callers iterate lines and access [DocumentLine.product].
   */
  @Query(
    """
      SELECT dl FROM DocumentLine dl
      LEFT JOIN FETCH dl.product
      WHERE dl.documentType = :documentType AND dl.documentId = :documentId
      ORDER BY dl.position
    """
  )
  fun findWithProductByDocumentTypeAndDocumentId(
    documentType: DocumentLine.DocumentType,
    documentId: Long,
  ): List<DocumentLine>
}
