package fr.axl.lvy.client

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.user.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "clients")
class Client(
  @NotBlank
  @Column(name = "client_code", nullable = false, unique = true, length = 20)
  var clientCode: String = "",
  @NotBlank @Column(nullable = false) var name: String,
) : SoftDeletableEntity() {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
  var type: ClientType = ClientType.COMPANY

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
  var role: ClientRole = ClientRole.CLIENT

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "visible_company", nullable = false, length = 5, columnDefinition = "varchar(5)")
  var visibleCompany: User.Company = User.Company.AB

  var email: String? = null

  @Column(length = 30) var phone: String? = null

  var website: String? = null

  @Column(length = 20) var siret: String? = null

  @Column(name = "vat_number", length = 20) var vatNumber: String? = null

  @Column(name = "billing_address", columnDefinition = "TEXT") var billingAddress: String? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(name = "payment_delay") var paymentDelay: Int? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_term_id")
  var paymentTerm: PaymentTerm? = null

  @Column(name = "default_discount", nullable = false, precision = 5, scale = 2)
  var defaultDiscount: BigDecimal = BigDecimal.ZERO

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
  var status: Status = Status.ACTIVE

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @OneToMany(mappedBy = "client", cascade = [CascadeType.ALL], orphanRemoval = true)
  var contacts: MutableList<Contact> = mutableListOf()

  fun isClient(): Boolean = role == ClientRole.CLIENT || role == ClientRole.BOTH

  fun isProducer(): Boolean = role == ClientRole.PRODUCER || role == ClientRole.BOTH

  fun isSupplierForProduct(): Boolean = isProducer() || role == ClientRole.OWN_COMPANY

  enum class ClientType {
    COMPANY,
    INDIVIDUAL,
    OWN_COMPANY,
  }

  enum class ClientRole {
    CLIENT,
    PRODUCER,
    BOTH,
    OWN_COMPANY,
  }

  enum class Status {
    ACTIVE,
    INACTIVE,
  }
}
