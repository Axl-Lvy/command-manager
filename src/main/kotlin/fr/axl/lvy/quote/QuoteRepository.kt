package fr.axl.lvy.quote

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface QuoteRepository : JpaRepository<Quote, Long> {

  @Query("SELECT q FROM Quote q LEFT JOIN FETCH q.client WHERE q.deletedAt IS NULL")
  fun findByDeletedAtIsNull(): List<Quote>
}
