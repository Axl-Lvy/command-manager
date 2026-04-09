package fr.axl.lvy.incoterm

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface IncotermRepository : JpaRepository<Incoterm, Long> {
  fun findAllByOrderByNameAsc(): List<Incoterm>

  fun findByNameIgnoreCase(name: String): Optional<Incoterm>
}
