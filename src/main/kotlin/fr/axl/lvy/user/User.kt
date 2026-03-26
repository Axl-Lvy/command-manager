package fr.axl.lvy.user

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
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role = Role.COLLABORATOR

  @Enumerated(EnumType.STRING) @Column(name = "company_id") var companyId: Company? = null

  var active: Boolean = true

  @Column(name = "last_login") var lastLogin: Instant? = null

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

  fun canSee(visibleCompany: Company): Boolean {
    if (companyId == null || companyId == Company.AB) return true
    return companyId == visibleCompany || visibleCompany == Company.AB
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as User
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

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
