package fr.axl.lvy.fiscalposition

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

@Entity
@Table(name = "fiscal_positions")
class FiscalPosition(
  @field:NotBlank @Column(nullable = false, unique = true, length = 100) var position: String
) : BaseEntity()
