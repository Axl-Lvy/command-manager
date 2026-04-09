package fr.axl.lvy.sale

import fr.axl.lvy.base.TotalizableDocument
import fr.axl.lvy.order.OrderB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Entity
@Table(name = "sales_b")
class SalesB(
  @field:NotBlank
  @Column(name = "sale_number", nullable = false, unique = true, length = 20)
  var saleNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_a_id", nullable = false)
  var salesA: SalesA,
) : TotalizableDocument() {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: SalesBStatus = SalesBStatus.DRAFT

  @Column(name = "sale_date") var saleDate: LocalDate? = null

  @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_b_id") var orderB: OrderB? = null

  enum class SalesBStatus {
    DRAFT,
    VALIDATED,
    CANCELLED,
  }
}
