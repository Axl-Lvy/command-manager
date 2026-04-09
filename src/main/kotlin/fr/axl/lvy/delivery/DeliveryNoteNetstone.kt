package fr.axl.lvy.delivery

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.order.OrderNetstone
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A delivery note for goods received from a supplier (Netstone side). Linked to the originating
 * [OrderNetstone]. Tracks container, lot, and seals for import logistics.
 *
 * Status workflow: IN_TRANSIT -> ARRIVED -> INSPECTED (or INCIDENT at any stage).
 */
@Entity
@Table(name = "delivery_notes_netstone")
class DeliveryNoteNetstone(
  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  var deliveryNoteNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_netstone_id", nullable = false)
  var orderNetstone: OrderNetstone,
) : SoftDeletableEntity() {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(
    nullable = false,
    columnDefinition = "enum('IN_TRANSIT','ARRIVED','INSPECTED','INCIDENT')",
  )
  var status: DeliveryNoteNetstoneStatus = DeliveryNoteNetstoneStatus.IN_TRANSIT

  @Column(name = "shipping_date") var shippingDate: LocalDate? = null

  @Column(name = "arrival_date") var arrivalDate: LocalDate? = null

  @Column(name = "container_number", length = 50) var containerNumber: String? = null

  @Column(length = 50) var lot: String? = null

  @Column(length = 100) var seals: String? = null

  @Column(name = "pdf_path", length = 500) var pdfPath: String? = null

  @Column(columnDefinition = "TEXT") var observations: String? = null

  enum class DeliveryNoteNetstoneStatus {
    IN_TRANSIT,
    ARRIVED,
    INSPECTED,
    INCIDENT,
  }
}
