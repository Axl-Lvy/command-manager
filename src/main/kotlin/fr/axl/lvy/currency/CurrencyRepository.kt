package fr.axl.lvy.currency

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface CurrencyRepository : JpaRepository<Currency, Long> {
  fun findAllByOrderByCodeAsc(): List<Currency>

  fun findByCodeIgnoreCase(code: String): Optional<Currency>
}
