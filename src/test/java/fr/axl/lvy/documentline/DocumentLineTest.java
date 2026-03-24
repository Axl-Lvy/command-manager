package fr.axl.lvy.documentline;

import static org.assertj.core.api.Assertions.assertThat;

import fr.axl.lvy.product.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DocumentLineTest {

  @Test
  void recalculate_computes_line_total_without_discount() {
    var line = new DocumentLine(DocumentLine.DocumentType.QUOTE, 1L, "Widget");
    line.setQuantity(new BigDecimal("10"));
    line.setUnitPriceExclTax(new BigDecimal("25.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));

    line.recalculate();

    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("250.00");
    assertThat(line.getVatAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void recalculate_applies_discount_percentage() {
    var line = new DocumentLine(DocumentLine.DocumentType.ORDER_A, 1L, "Widget");
    line.setQuantity(new BigDecimal("10"));
    line.setUnitPriceExclTax(new BigDecimal("100.00"));
    line.setDiscountPercent(new BigDecimal("15.00"));
    line.setVatRate(new BigDecimal("20.00"));

    line.recalculate();

    // 10 * 100 * (1 - 0.15) = 850.00
    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("850.00");
    // 850 * 20 / 100 = 170.00
    assertThat(line.getVatAmount()).isEqualByComparingTo("170.00");
  }

  @Test
  void recalculate_rounds_to_two_decimals() {
    var line = new DocumentLine(DocumentLine.DocumentType.QUOTE, 1L, "Widget");
    line.setQuantity(new BigDecimal("3"));
    line.setUnitPriceExclTax(new BigDecimal("10.33"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));

    line.recalculate();

    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("30.99");
    assertThat(line.getVatAmount()).isEqualByComparingTo("6.20");
  }

  @Test
  void fromProduct_creates_line_with_product_data() {
    var product = new Product("REF-001", "Steel Beam");
    product.setSellingPriceExclTax(new BigDecimal("150.00"));
    product.setUnit("kg");
    product.setHsCode("7216.10");
    product.setMadeIn("France");
    product.setClientProductCode("CL-BEAM-01");

    var line = DocumentLine.fromProduct(DocumentLine.DocumentType.QUOTE, 1L, product);

    assertThat(line.getDesignation()).isEqualTo("Steel Beam");
    assertThat(line.getUnitPriceExclTax()).isEqualByComparingTo("150.00");
    assertThat(line.getUnit()).isEqualTo("kg");
    assertThat(line.getHsCode()).isEqualTo("7216.10");
    assertThat(line.getMadeIn()).isEqualTo("France");
    assertThat(line.getClientProductCode()).isEqualTo("CL-BEAM-01");
    assertThat(line.getQuantity()).isEqualByComparingTo("1");
    assertThat(line.getDiscountPercent()).isEqualByComparingTo("0");
    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("150.00");
  }

  @Test
  void recalculate_with_zero_quantity_gives_zero() {
    var line = new DocumentLine(DocumentLine.DocumentType.QUOTE, 1L, "Widget");
    line.setQuantity(BigDecimal.ZERO);
    line.setUnitPriceExclTax(new BigDecimal("100.00"));
    line.setDiscountPercent(BigDecimal.ZERO);
    line.setVatRate(new BigDecimal("20.00"));

    line.recalculate();

    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("0.00");
    assertThat(line.getVatAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void recalculate_with_100_percent_discount_gives_zero() {
    var line = new DocumentLine(DocumentLine.DocumentType.QUOTE, 1L, "Widget");
    line.setQuantity(new BigDecimal("5"));
    line.setUnitPriceExclTax(new BigDecimal("200.00"));
    line.setDiscountPercent(new BigDecimal("100.00"));
    line.setVatRate(new BigDecimal("20.00"));

    line.recalculate();

    assertThat(line.getLineTotalExclTax()).isEqualByComparingTo("0.00");
    assertThat(line.getVatAmount()).isEqualByComparingTo("0.00");
  }
}
