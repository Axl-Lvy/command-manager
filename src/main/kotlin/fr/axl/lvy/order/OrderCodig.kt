package fr.axl.lvy.order

import fr.axl.lvy.base.CodigDocument
import fr.axl.lvy.client.Client
import fr.axl.lvy.delivery.DeliveryNoteCodig
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.invoice.InvoiceCodig
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "orders_codig")
class OrderCodig(
  @field:NotBlank
  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  var orderNumber: String,
  client: Client,
  @Column(name = "order_date", nullable = false) var orderDate: LocalDate,
) : CodigDocument(client) {
  companion object {
    private val EDITABLE =
      setOf(
        OrderCodigStatus.DRAFT,
        OrderCodigStatus.CONFIRMED,
        OrderCodigStatus.IN_PRODUCTION,
        OrderCodigStatus.READY,
      )
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_order_id")
  var sourceOrder: OrderCodig? = null

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(
    nullable = false,
    columnDefinition =
      "enum('DRAFT','CONFIRMED','IN_PRODUCTION','READY','DELIVERED','INVOICED','CANCELLED')",
  )
  var status: OrderCodigStatus = OrderCodigStatus.DRAFT

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  var vatRate: BigDecimal = BigDecimal.ZERO

  @Column(name = "margin_excl_tax", nullable = false, precision = 12, scale = 2)
  var marginExclTax: BigDecimal = BigDecimal.ZERO
    private set

  @OneToOne(mappedBy = "orderCodig", fetch = FetchType.LAZY)
  var orderNetstone: OrderNetstone? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_note_id")
  var deliveryNote: DeliveryNoteCodig? = null

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id")
  var invoice: InvoiceCodig? = null

  fun isEditable(): Boolean = EDITABLE.contains(status)

  override fun recalculateTotals(lines: List<DocumentLine>) {
    super.recalculateTotals(lines)
    marginExclTax = BigDecimal.ZERO
  }

  enum class OrderCodigStatus {
    DRAFT,
    CONFIRMED,
    IN_PRODUCTION,
    READY,
    DELIVERED,
    INVOICED,
    CANCELLED,
  }
}
