package fr.axl.lvy.base

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NumberSequenceRepository : JpaRepository<NumberSequence, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT n FROM NumberSequence n WHERE n.entityType = :type")
  fun findForUpdate(@Param("type") type: String): NumberSequence?
}
