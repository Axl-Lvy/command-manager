package fr.axl.lvy.client.contact

import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Entity
@Table(name = "contacts")
class Contact(@NotBlank @Column(name = "last_name", nullable = false) var lastName: String) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  lateinit var client: Client

  @Column(name = "first_name") var firstName: String? = null

  var email: String? = null

  @Column(length = 30) var phone: String? = null

  @Column(length = 30) var mobile: String? = null

  @Column(name = "job_title", length = 100) var jobTitle: String? = null

  @Enumerated(EnumType.STRING) @Column(nullable = false) var role: ContactRole = ContactRole.OTHER

  var active: Boolean = true

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  constructor(client: Client, lastName: String) : this(lastName) {
    this.client = client
  }

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as Contact
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class ContactRole {
    PRIMARY,
    BILLING,
    TECHNICAL,
    OTHER,
  }
}
