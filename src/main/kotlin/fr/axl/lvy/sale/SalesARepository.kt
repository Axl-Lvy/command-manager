package fr.axl.lvy.sale

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SalesARepository : JpaRepository<SalesA, Long> {

  @Query("SELECT s FROM SalesA s LEFT JOIN FETCH s.client WHERE s.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<SalesA>
}
