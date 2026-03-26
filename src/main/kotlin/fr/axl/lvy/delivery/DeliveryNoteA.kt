package fr.axl.lvy.delivery

import fr.axl.lvy.client.Client
import fr.axl.lvy.order.OrderA
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "delivery_notes_a")
class DeliveryNoteA(
  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  var deliveryNoteNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_a_id", nullable = false)
  var orderA: OrderA,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: DeliveryNoteAStatus = DeliveryNoteAStatus.PREPARED

  @Column(name = "shipping_date") var shippingDate: LocalDate? = null

  @Column(name = "delivery_date") var deliveryDate: LocalDate? = null

  @Column(name = "shipping_address", columnDefinition = "TEXT") var shippingAddress: String? = null

  @Column(length = 100) var carrier: String? = null

  @Column(name = "tracking_number", length = 100) var trackingNumber: String? = null

  @Column(name = "package_count") var packageCount: Int? = null

  @Column(name = "signed_by", length = 100) var signedBy: String? = null

  @Column(name = "signature_date") var signatureDate: LocalDate? = null

  @Column(columnDefinition = "TEXT") var observations: String? = null

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant? = null
    private set

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant? = null
    private set

  @Column(name = "deleted_at")
  var deletedAt: Instant? = null
    private set

  @PrePersist
  fun prePersist() {
    createdAt = Instant.now()
    updatedAt = Instant.now()
  }

  @PreUpdate
  fun preUpdate() {
    updatedAt = Instant.now()
  }

  fun isDeleted(): Boolean = deletedAt != null

  fun softDelete() {
    deletedAt = Instant.now()
  }

  fun restore() {
    deletedAt = null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || !javaClass.isAssignableFrom(other.javaClass)) return false
    other as DeliveryNoteA
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class DeliveryNoteAStatus {
    PREPARED,
    SHIPPED,
    DELIVERED,
    INCIDENT,
  }
}
