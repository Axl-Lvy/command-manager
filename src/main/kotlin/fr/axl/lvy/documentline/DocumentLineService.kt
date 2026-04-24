package fr.axl.lvy.documentline

import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages document lines across all document types (sales, orders, invoices). */
@Service
class DocumentLineService(private val documentLineRepository: DocumentLineRepository) {

  @Transactional(readOnly = true)
  fun findLines(documentType: DocumentLine.DocumentType, documentId: Long): List<DocumentLine> =
    documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(documentType, documentId)

  /** Same as [findLines] but eagerly fetches the product to avoid N+1 on line.product access. */
  @Transactional(readOnly = true)
  fun findLinesWithProduct(
    documentType: DocumentLine.DocumentType,
    documentId: Long,
  ): List<DocumentLine> =
    documentLineRepository.findWithProductByDocumentTypeAndDocumentId(documentType, documentId)

  /**
   * Atomically replaces all lines of a document: deletes existing lines, then persists new ones.
   * Optionally applies a [filter] and/or overrides the VAT rate or unit price on lines.
   */
  @Transactional
  fun replaceLines(
    documentType: DocumentLine.DocumentType,
    documentId: Long,
    lines: List<DocumentLine>,
    overrideVatRate: BigDecimal? = null,
    overrideUnitPrice: ((DocumentLine) -> BigDecimal?)? = null,
    overrideDiscountPercent: BigDecimal? = null,
    filter: ((DocumentLine) -> Boolean)? = null,
  ): List<DocumentLine> {
    val existingLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
        documentType,
        documentId,
      )
    documentLineRepository.deleteAll(existingLines)

    val sourceLines = if (filter != null) lines.filter(filter) else lines
    val persistedLines =
      sourceLines.mapIndexed { i, line ->
        DocumentLine(documentType, documentId, line.designation).apply {
          copyFieldsFrom(
            line,
            overrideVatRate = overrideVatRate,
            overrideUnitPrice = overrideUnitPrice?.invoke(line),
            overrideDiscountPercent = overrideDiscountPercent,
          )
          position = i
        }
      }
    return documentLineRepository.saveAll(persistedLines)
  }
}
