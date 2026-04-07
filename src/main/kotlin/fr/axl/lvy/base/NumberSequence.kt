package fr.axl.lvy.base

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "number_sequences")
class NumberSequence(
  @Id @Column(name = "entity_type", length = 20) val entityType: String,
  @Column(name = "next_val", nullable = false) var nextVal: Long = 1,
)
