package fr.axl.lvy.base

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as BaseEntity
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()
}
