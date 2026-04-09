package fr.axl.lvy.client.contact

import fr.axl.lvy.base.BaseEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "contacts")
class Contact(@NotBlank @Column(name = "last_name", nullable = false) var lastName: String) :
  BaseEntity() {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  lateinit var client: Client

  @Column(name = "first_name") var firstName: String? = null

  var email: String? = null

  @Column(length = 30) var phone: String? = null

  @Column(length = 30) var mobile: String? = null

  @Column(name = "job_title", length = 100) var jobTitle: String? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('PRIMARY','BILLING','TECHNICAL','OTHER')")
  var role: ContactRole = ContactRole.OTHER

  var active: Boolean = true

  constructor(client: Client, lastName: String) : this(lastName) {
    this.client = client
  }

  enum class ContactRole {
    PRIMARY,
    BILLING,
    TECHNICAL,
    OTHER,
  }
}
