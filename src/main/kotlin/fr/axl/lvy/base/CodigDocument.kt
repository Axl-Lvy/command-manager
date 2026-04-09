package fr.axl.lvy.base

import fr.axl.lvy.client.Client
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * A customer-facing document issued by the Codig company. Extends [TotalizableDocument] with client
 * reference, addresses, currency / exchange rate, and contractual conditions.
 */
@MappedSuperclass
abstract class CodigDocument(
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client
) : TotalizableDocument() {
  @Column(name = "client_reference", length = 100) var clientReference: String? = null

  var subject: String? = null

  @Column(name = "billing_address", columnDefinition = "TEXT") var billingAddress: String? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @Column(columnDefinition = "TEXT") var conditions: String? = null
}
