package fr.axl.lvy.delivery

import fr.axl.lvy.base.SoftDeletableEntity
import fr.axl.lvy.client.Client
import fr.axl.lvy.order.OrderCodig
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A delivery note for goods shipped to a customer (Codig side). Linked to the originating
 * [OrderCodig]. Tracks shipping, carrier, and proof of delivery.
 *
 * Status workflow: PREPARED -> SHIPPED -> DELIVERED (or INCIDENT at any stage).
 */
@Entity
@Table(name = "delivery_notes_codig")
class DeliveryNoteCodig(
  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  var deliveryNoteNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_codig_id", nullable = false)
  var orderCodig: OrderCodig,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
) : SoftDeletableEntity() {
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, columnDefinition = "enum('PREPARED','SHIPPED','DELIVERED','INCIDENT')")
  var status: DeliveryNoteCodigStatus = DeliveryNoteCodigStatus.PREPARED

  @Column(name = "shipping_date") var shippingDate: LocalDate? = null

  @Column(name = "delivery_date") var deliveryDate: LocalDate? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(length = 100) var carrier: String? = null

  @Column(name = "tracking_number", length = 100) var trackingNumber: String? = null

  @Column(name = "package_count") var packageCount: Int? = null

  @Column(name = "signed_by", length = 100) var signedBy: String? = null

  @Column(name = "signature_date") var signatureDate: LocalDate? = null

  @Column(columnDefinition = "TEXT") var observations: String? = null

  enum class DeliveryNoteCodigStatus {
    PREPARED,
    SHIPPED,
    DELIVERED,
    INCIDENT,
  }
}
