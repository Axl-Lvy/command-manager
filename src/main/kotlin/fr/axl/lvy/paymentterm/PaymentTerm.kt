package fr.axl.lvy.paymentterm

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

@Entity
@Table(name = "payment_terms")
class PaymentTerm(
  @field:NotBlank @Column(nullable = false, unique = true, length = 100) var label: String
) : BaseEntity()
