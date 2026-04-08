package fr.axl.lvy.documentline

import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class DocumentLineServiceTest {

  @Autowired lateinit var documentLineService: DocumentLineService
  @Autowired lateinit var documentLineRepository: DocumentLineRepository

  @Test
  fun replaceLines_deletes_existing_and_creates_new() {
    val docType = DocumentLine.DocumentType.ORDER_A
    val docId = 999L

    val existing = DocumentLine(docType, docId, "Old line")
    existing.quantity = BigDecimal.ONE
    existing.unitPriceExclTax = BigDecimal("10.00")
    existing.recalculate()
    documentLineRepository.saveAndFlush(existing)

    val newLine = DocumentLine(docType, 0L, "New line")
    newLine.quantity = BigDecimal("3")
    newLine.unitPriceExclTax = BigDecimal("50.00")
    newLine.discountPercent = BigDecimal.ZERO
    newLine.vatRate = BigDecimal("20.00")
    newLine.recalculate()

    val result = documentLineService.replaceLines(docType, docId, listOf(newLine))

    assertThat(result).hasSize(1)
    assertThat(result[0].designation).isEqualTo("New line")
    assertThat(result[0].documentId).isEqualTo(docId)
    assertThat(result[0].documentType).isEqualTo(docType)
    assertThat(result[0].quantity).isEqualByComparingTo("3")
    assertThat(result[0].lineTotalExclTax).isEqualByComparingTo("150.00")
    assertThat(result[0].position).isEqualTo(0)

    val allLines =
      documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(docType, docId)
    assertThat(allLines).hasSize(1)
    assertThat(allLines[0].designation).isEqualTo("New line")
  }

  @Test
  fun replaceLines_with_overrideVatRate() {
    val docType = DocumentLine.DocumentType.SALES_A
    val docId = 998L

    val line = DocumentLine(docType, 0L, "Item")
    line.quantity = BigDecimal("2")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("10.00")
    line.recalculate()

    val result =
      documentLineService.replaceLines(
        docType,
        docId,
        listOf(line),
        overrideVatRate = BigDecimal("20.00"),
      )

    assertThat(result[0].vatRate).isEqualByComparingTo("20.00")
    assertThat(result[0].vatAmount).isEqualByComparingTo("40.00")
  }

  @Test
  fun replaceLines_with_filter() {
    val docType = DocumentLine.DocumentType.SALES_B
    val docId = 997L

    val line1 = DocumentLine(docType, 0L, "Keep")
    line1.quantity = BigDecimal.ONE
    line1.unitPriceExclTax = BigDecimal("10.00")
    line1.recalculate()

    val line2 = DocumentLine(docType, 0L, "Remove")
    line2.quantity = BigDecimal.ONE
    line2.unitPriceExclTax = BigDecimal("20.00")
    line2.recalculate()

    val result =
      documentLineService.replaceLines(
        docType,
        docId,
        listOf(line1, line2),
        filter = { it.designation == "Keep" },
      )

    assertThat(result).hasSize(1)
    assertThat(result[0].designation).isEqualTo("Keep")
  }

  @Test
  fun findLines_returns_lines_ordered_by_position() {
    val docType = DocumentLine.DocumentType.ORDER_B
    val docId = 996L

    val line2 = DocumentLine(docType, docId, "Second")
    line2.position = 1
    line2.recalculate()
    documentLineRepository.save(line2)

    val line1 = DocumentLine(docType, docId, "First")
    line1.position = 0
    line1.recalculate()
    documentLineRepository.save(line1)
    documentLineRepository.flush()

    val result = documentLineService.findLines(docType, docId)

    assertThat(result).hasSize(2)
    assertThat(result[0].designation).isEqualTo("First")
    assertThat(result[1].designation).isEqualTo("Second")
  }
}
