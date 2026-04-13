package fr.axl.lvy.sale

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.order.OrderNetstone
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * The Netstone-side mirror of a [SalesCodig], automatically created when the Codig sale contains
 * MTO products. Represents what Netstone needs to procure from suppliers. When validated, it
 * generates a linked [OrderNetstone].
 */
@Entity
@Table(name = "sales_b")
class SalesNetstone(
  @field:NotBlank
  @Column(name = "sale_number", nullable = false, unique = true, length = 20)
  var saleNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_codig_id", nullable = false)
  var salesCodig: SalesCodig,
) : TotalizableDocument() {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('DRAFT','VALIDATED','CANCELLED')")
  var status: SalesStatus = SalesStatus.DRAFT

  @Column(name = "sale_date") var saleDate: LocalDate? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiscal_position_id")
  var fiscalPosition: FiscalPosition? = null

  @Column(nullable = false, length = 5) var currency: String = "EUR"

  @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
  var exchangeRate: BigDecimal = BigDecimal.ONE

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_netstone_id")
  var orderNetstone: OrderNetstone? = null
}
