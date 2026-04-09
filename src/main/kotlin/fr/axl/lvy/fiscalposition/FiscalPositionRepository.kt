package fr.axl.lvy.fiscalposition

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface FiscalPositionRepository : JpaRepository<FiscalPosition, Long> {
  fun findAllByOrderByPositionAsc(): List<FiscalPosition>

  fun findByPositionIgnoreCase(position: String): Optional<FiscalPosition>
}
