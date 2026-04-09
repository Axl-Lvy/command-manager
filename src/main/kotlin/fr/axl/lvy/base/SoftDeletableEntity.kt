package fr.axl.lvy.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * Entity that supports soft deletion. Instead of being removed from the database, a [deletedAt]
 * timestamp is set and the record is filtered out by queries.
 */
@MappedSuperclass
abstract class SoftDeletableEntity : BaseEntity() {
  @Column(name = "deleted_at")
  var deletedAt: Instant? = null
    private set

  fun isDeleted(): Boolean = deletedAt != null

  fun softDelete() {
    deletedAt = Instant.now()
  }

  fun restore() {
    deletedAt = null
  }
}
