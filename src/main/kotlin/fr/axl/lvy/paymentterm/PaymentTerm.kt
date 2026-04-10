package fr.axl.lvy.paymentterm

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank

/**
 * A payment condition label (e.g. "30 days net", "50% advance + 50% on delivery") assigned to
 * clients or documents.
 */
@Entity
@Table(name = "payment_terms")
class PaymentTerm(
  @field:NotBlank @Column(nullable = false, unique = true, length = 100) var label: String
) : BaseEntity()
