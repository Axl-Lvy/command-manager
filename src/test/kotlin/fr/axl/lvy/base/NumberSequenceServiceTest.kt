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
    val first = numberSequenceService.nextNumber(NumberSequenceService.ORDER_A, "CoD_PO_", 3)
    val second = numberSequenceService.nextNumber(NumberSequenceService.ORDER_A, "CoD_PO_", 3)

    assertThat(first).isEqualTo("CoD_PO_001")
    assertThat(second).isEqualTo("CoD_PO_002")
  }

  @Test
  fun nextNumber_independent_per_entity_type() {
    val orderA = numberSequenceService.nextNumber(NumberSequenceService.ORDER_A, "CoD_PO_", 3)
    val orderB = numberSequenceService.nextNumber(NumberSequenceService.ORDER_B, "NST_PO_", 3)

    assertThat(orderA).isEqualTo("CoD_PO_001")
    assertThat(orderB).isEqualTo("NST_PO_001")
  }

  @Test
  fun nextNumber_formats_with_prefix_and_padding() {
    val client = numberSequenceService.nextNumber(NumberSequenceService.CLIENT, "C", 6)

    assertThat(client).isEqualTo("C000001")
  }
}
