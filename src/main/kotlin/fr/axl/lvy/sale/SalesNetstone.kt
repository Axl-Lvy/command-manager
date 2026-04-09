package fr.axl.lvy.sale

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.order.OrderNetstone
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

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

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_netstone_id")
  var orderNetstone: OrderNetstone? = null
}
