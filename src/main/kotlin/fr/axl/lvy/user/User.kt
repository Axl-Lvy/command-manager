package fr.axl.lvy.user

import fr.axl.lvy.base.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A system user with role-based access. The [companyId] controls which company's data the user can
 * see (Codig only, Netstone only, or both).
 */
@Entity
@Table(name = "users")
class User(
  @NotBlank @Column(nullable = false) var name: String,
  @NotBlank @Email @Column(nullable = false, unique = true) var email: String,
  @NotBlank @Column(nullable = false) var password: String,
) : BaseEntity() {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('ADMIN','COLLABORATOR','ACCOUNTANT')")
  var role: Role = Role.COLLABORATOR

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "company_id", columnDefinition = "enum('CODIG','NETSTONE','BOTH')")
  var companyId: Company? = null

  var active: Boolean = true

  @Column(name = "last_login") var lastLogin: Instant? = null

  /** Returns true if this user is allowed to see data tagged with [visibleCompany]. */
  fun canSee(visibleCompany: Company): Boolean {
    if (companyId == null || companyId == Company.BOTH) return true
    return companyId == visibleCompany || visibleCompany == Company.BOTH
  }

  enum class Role {
    ADMIN,
    COLLABORATOR,
    ACCOUNTANT,
  }

  enum class Company {
    CODIG,
    NETSTONE,
    BOTH,
  }
}
