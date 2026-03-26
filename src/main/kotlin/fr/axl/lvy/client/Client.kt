package fr.axl.lvy.client

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.user.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(name = "clients")
class Client(
  @NotBlank
  @Column(name = "client_code", nullable = false, unique = true, length = 20)
  var clientCode: String,
  @NotBlank @Column(nullable = false) var name: String,
) : SoftDeletableEntity() {
  @Enumerated(EnumType.STRING) @Column(nullable = false) var type: ClientType = ClientType.COMPANY

  @Enumerated(EnumType.STRING) @Column(nullable = false) var role: ClientRole = ClientRole.CLIENT

  @Enumerated(EnumType.STRING)
  @Column(name = "visible_company", nullable = false)
  var visibleCompany: User.Company = User.Company.A

  var email: String? = null

  @Column(length = 30) var phone: String? = null

  var website: String? = null

  @Column(length = 20) var siret: String? = null

  @Column(name = "vat_number", length = 20) var vatNumber: String? = null

  @Column(name = "billing_address", columnDefinition = "TEXT") var billingAddress: String? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(name = "payment_delay") var paymentDelay: Int? = null

  @Column(name = "payment_method", length = 50) var paymentMethod: String? = null

  @Column(name = "default_discount", nullable = false, precision = 5, scale = 2)
  var defaultDiscount: BigDecimal = BigDecimal.ZERO

  @Enumerated(EnumType.STRING) @Column(nullable = false) var status: Status = Status.ACTIVE

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @OneToMany(mappedBy = "client", cascade = [CascadeType.ALL], orphanRemoval = true)
  var contacts: MutableList<Contact> = mutableListOf()

  fun isClient(): Boolean = role == ClientRole.CLIENT || role == ClientRole.BOTH

  fun isProducer(): Boolean = role == ClientRole.PRODUCER || role == ClientRole.BOTH

  enum class ClientType {
    COMPANY,
    INDIVIDUAL,
  }

  enum class ClientRole {
    CLIENT,
    PRODUCER,
    BOTH,
  }

  enum class Status {
    ACTIVE,
    INACTIVE,
  }
}
