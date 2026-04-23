package fr.axl.lvy.base

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class NumberSequenceServiceTest {

  @Autowired lateinit var numberSequenceService: NumberSequenceService

  @Test
  fun nextNumber_returns_sequential_values() {
    val first = numberSequenceService.nextNumber(NumberSequenceService.ORDER_CODIG, "CoD_PO_", 3)
    val second = numberSequenceService.nextNumber(NumberSequenceService.ORDER_CODIG, "CoD_PO_", 3)

    assertThat(first).isEqualTo("CoD_PO_001")
    assertThat(second).isEqualTo("CoD_PO_002")
  }

  @Test
  fun nextNumber_independent_per_entity_type() {
    val orderCodig =
      numberSequenceService.nextNumber(NumberSequenceService.ORDER_CODIG, "CoD_PO_", 3)
    val orderNetstone =
      numberSequenceService.nextNumber(NumberSequenceService.ORDER_NETSTONE, "NST_PO_", 3)

    assertThat(orderCodig).isEqualTo("CoD_PO_001")
    assertThat(orderNetstone).isEqualTo("NST_PO_001")
  }

  @Test
  fun nextNumber_formats_with_prefix_and_padding() {
    val client = numberSequenceService.nextNumber(NumberSequenceService.CLIENT, "C", 6)

    assertThat(client).isEqualTo("C000001")
  }

  @Test
  fun nextNumber_auto_creates_sequence_when_missing() {
    val first = numberSequenceService.nextNumber("NEW_TYPE", "NEW_", 4)
    val second = numberSequenceService.nextNumber("NEW_TYPE", "NEW_", 4)

    assertThat(first).isEqualTo("NEW_0001")
    assertThat(second).isEqualTo("NEW_0002")
  }

  @Test
  fun nextNumber_single_param_uses_config() {
    val client = numberSequenceService.nextNumber(NumberSequenceService.CLIENT)
    assertThat(client).isEqualTo("C000001")

    val orderCodig = numberSequenceService.nextNumber(NumberSequenceService.ORDER_CODIG)
    assertThat(orderCodig).isEqualTo("CoD_PO_001")
  }

  @Test
  fun nextNumber_throws_for_unknown_entity_type() {
    org.assertj.core.api.Assertions.assertThatThrownBy {
        numberSequenceService.nextNumber("NO_SUCH_TYPE")
      }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun previewNextNumber_returns_next_without_advancing_sequence() {
    val preview = numberSequenceService.previewNextNumber(NumberSequenceService.DELIVERY_NETSTONE)
    val next = numberSequenceService.nextNumber(NumberSequenceService.DELIVERY_NETSTONE)
    val secondPreview =
      numberSequenceService.previewNextNumber(NumberSequenceService.DELIVERY_NETSTONE)

    assertThat(preview).isEqualTo("Netst/OUT/001")
    assertThat(next).isEqualTo(preview)
    assertThat(secondPreview).isEqualTo("Netst/OUT/002")
  }

  @Test
  fun previewNextNumber_throws_for_unknown_entity_type() {
    org.assertj.core.api.Assertions.assertThatThrownBy {
        numberSequenceService.previewNextNumber("NO_SUCH_TYPE")
      }
      .isInstanceOf(IllegalArgumentException::class.java)
  }
}
