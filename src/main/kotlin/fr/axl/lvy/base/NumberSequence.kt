package fr.axl.lvy.base

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistent auto-incrementing counter used to generate unique business reference numbers (e.g.
 * client codes, order numbers). One row per [entityType].
 */
@Entity
@Table(name = "number_sequences")
class NumberSequence(
  @Id @Column(name = "entity_type", length = 32) val entityType: String,
  @Column(name = "next_val", nullable = false) var nextVal: Long = 1,
)
