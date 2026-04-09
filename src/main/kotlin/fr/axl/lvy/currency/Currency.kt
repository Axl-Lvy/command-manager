package fr.axl.lvy.currency

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

@Entity
@Table(name = "currencies")
class Currency(
  @field:NotBlank @Column(nullable = false, unique = true, length = 10) var code: String,
  @field:NotBlank @Column(nullable = false, length = 10) var symbol: String,
  @field:NotBlank @Column(nullable = false, length = 100) var name: String,
) : BaseEntity()
