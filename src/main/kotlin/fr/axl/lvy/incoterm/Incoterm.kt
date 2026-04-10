package fr.axl.lvy.incoterm

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

/**
 * An ICC Incoterm rule (e.g. "FOB", "CIF") defining delivery responsibilities on international
 * shipments.
 */
@Entity
@Table(name = "incoterms")
class Incoterm(
  @field:NotBlank @Column(nullable = false, unique = true, length = 20) var name: String,
  @field:NotBlank @Column(nullable = false, length = 255) var label: String,
) : BaseEntity()
