package fr.axl.lvy.delivery;

import fr.axl.lvy.purchaseorder.PurchaseOrder;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "purchase_delivery_notes")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseDeliveryNote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  private String deliveryNoteNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PurchaseOrder purchaseOrder;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PurchaseDeliveryStatus status = PurchaseDeliveryStatus.IN_TRANSIT;

  @Nullable
  @Column(name = "shipping_date")
  private LocalDate shippingDate;

  @Nullable
  @Column(name = "arrival_date")
  private LocalDate arrivalDate;

  @Nullable
  @Column(name = "container_number", length = 50)
  private String containerNumber;

  @Nullable
  @Column(length = 50)
  private String lot;

  @Nullable
  @Column(length = 100)
  private String seals;

  @Nullable
  @Column(name = "pdf_path", length = 500)
  private String pdfPath;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String observations;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Nullable
  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "deleted_at")
  private Instant deletedAt;

  public PurchaseDeliveryNote(String deliveryNoteNumber, PurchaseOrder purchaseOrder) {
    this.deliveryNoteNumber = deliveryNoteNumber;
    this.purchaseOrder = purchaseOrder;
  }

  @PrePersist
  void prePersist() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deletedAt = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !getClass().isAssignableFrom(obj.getClass())) return false;
    PurchaseDeliveryNote other = (PurchaseDeliveryNote) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum PurchaseDeliveryStatus {
    IN_TRANSIT,
    ARRIVED,
    INSPECTED,
    INCIDENT
  }
}
