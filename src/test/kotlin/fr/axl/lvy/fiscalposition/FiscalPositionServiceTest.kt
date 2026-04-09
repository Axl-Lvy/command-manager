package fr.axl.lvy.fiscalposition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class FiscalPositionServiceTest {

  @Autowired lateinit var fiscalPositionService: FiscalPositionService
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository

  @Test
  fun save_normalizes_and_persists_fiscal_position() {
    val fiscalPosition = FiscalPosition(" Exonere UE ")

    val saved = fiscalPositionService.save(fiscalPosition)

    assertThat(saved.position).isEqualTo("Exonere UE")
    assertThat(fiscalPositionService.findById(saved.id!!)).isPresent
  }

  @Test
  fun save_rejects_duplicate_position_case_insensitive() {
    fiscalPositionService.save(FiscalPosition("France"))

    assertThatThrownBy { fiscalPositionService.save(FiscalPosition("FRANCE")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("FRANCE")
  }

  @Test
  fun findAll_returns_fiscal_positions_sorted_by_position() {
    fiscalPositionService.save(FiscalPosition("Export"))
    fiscalPositionService.save(FiscalPosition("Domestique"))

    val fiscalPositions = fiscalPositionService.findAll()

    assertThat(fiscalPositions.map { it.position }).containsExactly("Domestique", "Export")
  }

  @Test
  fun delete_removes_fiscal_position() {
    val fiscalPosition = fiscalPositionService.save(FiscalPosition("Intracommunautaire"))

    fiscalPositionService.delete(fiscalPosition.id!!)
    fiscalPositionRepository.flush()

    assertThat(fiscalPositionService.findById(fiscalPosition.id!!)).isEmpty
  }
}
