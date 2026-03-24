package fr.axl.lvy.delivery;

import fr.axl.lvy.order.OrderB;
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
@Table(name = "delivery_notes_b")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryNoteB {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  private String deliveryNoteNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_b_id", nullable = false)
  private OrderB orderB;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeliveryNoteBStatus status = DeliveryNoteBStatus.IN_TRANSIT;

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

  public DeliveryNoteB(String deliveryNoteNumber, OrderB orderB) {
    this.deliveryNoteNumber = deliveryNoteNumber;
    this.orderB = orderB;
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
    DeliveryNoteB other = (DeliveryNoteB) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum DeliveryNoteBStatus {
    IN_TRANSIT,
    ARRIVED,
    INSPECTED,
    INCIDENT
  }
}
