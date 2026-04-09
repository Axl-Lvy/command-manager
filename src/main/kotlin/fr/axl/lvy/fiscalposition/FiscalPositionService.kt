package fr.axl.lvy.fiscalposition

import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages the fiscal position reference table. Enforces label uniqueness (case-insensitive). */
@Service
class FiscalPositionService(private val fiscalPositionRepository: FiscalPositionRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<FiscalPosition> = fiscalPositionRepository.findAllByOrderByPositionAsc()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<FiscalPosition> = fiscalPositionRepository.findById(id)

  @Transactional
  fun save(fiscalPosition: FiscalPosition): FiscalPosition {
    fiscalPosition.position = fiscalPosition.position.trim()
    val existing = fiscalPositionRepository.findByPositionIgnoreCase(fiscalPosition.position)
    require(!(existing.isPresent && existing.get().id != fiscalPosition.id)) {
      "Une position fiscale avec le libellé '${fiscalPosition.position}' existe déjà"
    }
    return fiscalPositionRepository.save(fiscalPosition)
  }

  @Transactional
  fun delete(id: Long) {
    if (fiscalPositionRepository.existsById(id)) {
      fiscalPositionRepository.deleteById(id)
    }
  }
}
