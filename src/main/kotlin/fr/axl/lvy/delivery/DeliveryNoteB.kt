package fr.axl.lvy.delivery

import fr.axl.lvy.order.OrderB
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "delivery_notes_b")
class DeliveryNoteB(
  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  var deliveryNoteNumber: String,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_b_id", nullable = false)
  var orderB: OrderB,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
    private set

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: DeliveryNoteBStatus = DeliveryNoteBStatus.IN_TRANSIT

  @Column(name = "shipping_date") var shippingDate: LocalDate? = null

  @Column(name = "arrival_date") var arrivalDate: LocalDate? = null

  @Column(name = "container_number", length = 50) var containerNumber: String? = null

  @Column(length = 50) var lot: String? = null

  @Column(length = 100) var seals: String? = null

  @Column(name = "pdf_path", length = 500) var pdfPath: String? = null

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
    other as DeliveryNoteB
    return id != null && id == other.id
  }

  override fun hashCode(): Int = javaClass.hashCode()

  enum class DeliveryNoteBStatus {
    IN_TRANSIT,
    ARRIVED,
    INSPECTED,
    INCIDENT,
  }
}
