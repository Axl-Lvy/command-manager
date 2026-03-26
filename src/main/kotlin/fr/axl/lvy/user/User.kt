package fr.axl.lvy.user

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Entity
@Table(name = "users")
class User(
  @NotBlank @Column(nullable = false) var name: String,
  @NotBlank @Email @Column(nullable = false, unique = true) var email: String,
  @NotBlank @Column(nullable = false) var password: String,
) : BaseEntity() {
  @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role = Role.COLLABORATOR

  @Enumerated(EnumType.STRING) @Column(name = "company_id") var companyId: Company? = null

  var active: Boolean = true

  @Column(name = "last_login") var lastLogin: Instant? = null

  fun canSee(visibleCompany: Company): Boolean {
    if (companyId == null || companyId == Company.AB) return true
    return companyId == visibleCompany || visibleCompany == Company.AB
  }

  enum class Role {
    ADMIN,
    COLLABORATOR,
    ACCOUNTANT,
  }

  enum class Company {
    A,
    B,
    AB,
  }
}
