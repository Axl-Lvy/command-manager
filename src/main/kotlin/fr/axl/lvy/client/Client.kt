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

/**
 * A business partner — can be a customer ([ClientRole.CLIENT]), a supplier/producer
 * ([ClientRole.PRODUCER]), or both. The [visibleCompany] field controls which company (Codig,
 * Netstone, or both) can see this client in their views.
 *
 * Clients of type [ClientType.OWN_COMPANY] represent Codig or Netstone themselves (used as
 * self-reference in inter-company transactions).
 */
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
  @Column(nullable = false, columnDefinition = "enum('COMPANY','INDIVIDUAL','OWN_COMPANY')")
  var type: ClientType = ClientType.COMPANY

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('CLIENT','PRODUCER','BOTH','OWN_COMPANY')")
  var role: ClientRole = ClientRole.CLIENT

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(
    name = "visible_company",
    nullable = false,
    columnDefinition = "enum('CODIG','NETSTONE','BOTH')",
  )
  var visibleCompany: User.Company = User.Company.BOTH

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
  @Column(nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  var status: Status = Status.ACTIVE

  @Column(columnDefinition = "TEXT") var notes: String? = null

  @OneToMany(mappedBy = "client", cascade = [CascadeType.ALL], orphanRemoval = true)
  var contacts: MutableList<Contact> = mutableListOf()

  /** Whether this client can appear as a buyer on Codig sales/orders. */
  fun isClient(): Boolean = role == ClientRole.CLIENT || role == ClientRole.BOTH

  /** Whether this client can appear as a supplier/manufacturer. */
  fun isProducer(): Boolean = role == ClientRole.PRODUCER || role == ClientRole.BOTH

  /**
   * Whether this client can be linked as a product supplier (includes own companies for
   * inter-company flows).
   */
  fun isSupplierForProduct(): Boolean = isProducer() || role == ClientRole.OWN_COMPANY

  enum class ClientType {
    COMPANY,
    INDIVIDUAL,

    /** Represents Codig or Netstone itself, used in inter-company transactions. */
    OWN_COMPANY,
  }

  enum class ClientRole {
    /** Buys from us. */
    CLIENT,

    /** Supplies / manufactures products for us. */
    PRODUCER,

    /** Acts as both customer and supplier. */
    BOTH,

    /** Internal own-company record (Codig or Netstone). */
    OWN_COMPANY,
  }

  enum class Status {
    ACTIVE,
    INACTIVE,
  }
}
