package fr.axl.lvy.documentline

import fr.axl.lvy.client.Client
import fr.axl.lvy.product.Product
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentLineTest {

  @Test
  fun recalculate_computes_line_total_without_discount() {
    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget")
    line.quantity = BigDecimal("10")
    line.unitPriceExclTax = BigDecimal("25.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    line.recalculate()

    assertThat(line.lineTotalExclTax).isEqualByComparingTo("250.00")
    assertThat(line.vatAmount).isEqualByComparingTo("50.00")
  }

  @Test
  fun recalculate_applies_discount_percentage() {
    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget")
    line.quantity = BigDecimal("10")
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal("15.00")
    line.vatRate = BigDecimal("20.00")

    line.recalculate()

    // 10 * 100 * (1 - 0.15) = 850.00
    assertThat(line.lineTotalExclTax).isEqualByComparingTo("850.00")
    // 850 * 20 / 100 = 170.00
    assertThat(line.vatAmount).isEqualByComparingTo("170.00")
  }

  @Test
  fun recalculate_rounds_to_two_decimals() {
    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget")
    line.quantity = BigDecimal("3")
    line.unitPriceExclTax = BigDecimal("10.33")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    line.recalculate()

    assertThat(line.lineTotalExclTax).isEqualByComparingTo("30.99")
    assertThat(line.vatAmount).isEqualByComparingTo("6.20")
  }

  @Test
  fun fromProduct_creates_line_with_product_data() {
    val client = Client("CLI-01", "Client 01")
    val product = Product("REF-001", "Steel Beam")
    product.sellingPriceExclTax = BigDecimal("150.00")
    product.unit = "kg"
    product.hsCode = "7216.10"
    product.madeIn = "France"
    product.replaceClientProductCodes(listOf(client to "CL-BEAM-01"))

    val line = DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_A, 1L, product, client)

    assertThat(line.designation).isEqualTo("Steel Beam")
    assertThat(line.unitPriceExclTax).isEqualByComparingTo("150.00")
    assertThat(line.unit).isEqualTo("kg")
    assertThat(line.hsCode).isEqualTo("7216.10")
    assertThat(line.madeIn).isEqualTo("France")
    assertThat(line.clientProductCode).isEqualTo("CL-BEAM-01")
    assertThat(line.quantity).isEqualByComparingTo("1")
    assertThat(line.discountPercent).isEqualByComparingTo("0")
    assertThat(line.lineTotalExclTax).isEqualByComparingTo("150.00")
  }

  @Test
  fun recalculate_with_zero_quantity_gives_zero() {
    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget")
    line.quantity = BigDecimal.ZERO
    line.unitPriceExclTax = BigDecimal("100.00")
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = BigDecimal("20.00")

    line.recalculate()

    assertThat(line.lineTotalExclTax).isEqualByComparingTo("0.00")
    assertThat(line.vatAmount).isEqualByComparingTo("0.00")
  }

  @Test
  fun fromProduct_without_client_sets_null_clientProductCode() {
    val product = Product("REF-002", "Copper Wire")
    product.sellingPriceExclTax = BigDecimal("50.00")
    product.unit = "m"
    product.hsCode = "7408.11"
    product.madeIn = "Germany"

    val line = DocumentLine.fromProduct(DocumentLine.DocumentType.INVOICE_A, 2L, product)

    assertThat(line.clientProductCode).isNull()
    assertThat(line.designation).isEqualTo("Copper Wire")
    assertThat(line.unitPriceExclTax).isEqualByComparingTo("50.00")
    assertThat(line.lineTotalExclTax).isEqualByComparingTo("50.00")
  }

  @Test
  fun fromProduct_with_client_not_having_code_sets_null_clientProductCode() {
    val client = Client("CLI-99", "Unknown Client")
    val product = Product("REF-003", "Bolt")
    product.sellingPriceExclTax = BigDecimal("2.00")

    val line = DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_B, 3L, product, client)

    assertThat(line.clientProductCode).isNull()
  }

  @Test
  fun recalculate_with_100_percent_discount_gives_zero() {
    val line = DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget")
    line.quantity = BigDecimal("5")
    line.unitPriceExclTax = BigDecimal("200.00")
    line.discountPercent = BigDecimal("100.00")
    line.vatRate = BigDecimal("20.00")

    line.recalculate()

    assertThat(line.lineTotalExclTax).isEqualByComparingTo("0.00")
    assertThat(line.vatAmount).isEqualByComparingTo("0.00")
  }
}
