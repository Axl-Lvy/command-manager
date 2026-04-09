package fr.axl.lvy.sale

import fr.axl.lvy.base.CodigDocument
import fr.axl.lvy.client.Client
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.paymentterm.PaymentTerm
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "sales_a")
class SalesCodig(
  @field:NotBlank
  @Column(name = "sale_number", nullable = false, unique = true, length = 20)
  var saleNumber: String,
  client: Client,
  @Column(name = "sale_date", nullable = false) var saleDate: LocalDate,
) : CodigDocument(client) {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('DRAFT','VALIDATED','CANCELLED')")
  var status: SalesStatus = SalesStatus.DRAFT

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_term_id")
  var paymentTerm: PaymentTerm? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_codig_id")
  var orderCodig: OrderCodig? = null

  @OneToOne(mappedBy = "salesCodig", fetch = FetchType.LAZY)
  var salesNetstone: SalesNetstone? = null
}
