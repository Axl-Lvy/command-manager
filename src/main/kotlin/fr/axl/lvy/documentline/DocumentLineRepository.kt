package fr.axl.lvy.documentline

import org.springframework.data.jpa.repository.JpaRepository

interface DocumentLineRepository : JpaRepository<DocumentLine, Long> {
  fun findByDocumentTypeAndDocumentIdOrderByPosition(
    documentType: DocumentLine.DocumentType,
    documentId: Long,
  ): List<DocumentLine>
}
