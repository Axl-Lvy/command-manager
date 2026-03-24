package fr.axl.lvy.delivery;

import fr.axl.lvy.client.Client;
import fr.axl.lvy.salesorder.SalesOrder;
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
@Table(name = "sales_delivery_notes")
@Getter
@Setter
@NoArgsConstructor
public class SalesDeliveryNote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "delivery_note_number", nullable = false, unique = true, length = 20)
  private String deliveryNoteNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sales_order_id", nullable = false)
  private SalesOrder salesOrder;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SalesDeliveryStatus status = SalesDeliveryStatus.PREPARED;

  @Nullable
  @Column(name = "shipping_date")
  private LocalDate shippingDate;

  @Nullable
  @Column(name = "delivery_date")
  private LocalDate deliveryDate;

  @Nullable
  @Column(name = "shipping_address", columnDefinition = "TEXT")
  private String shippingAddress;

  @Nullable
  @Column(length = 100)
  private String carrier;

  @Nullable
  @Column(name = "tracking_number", length = 100)
  private String trackingNumber;

  @Nullable
  @Column(name = "package_count")
  private Integer packageCount;

  @Nullable
  @Column(name = "signed_by", length = 100)
  private String signedBy;

  @Nullable
  @Column(name = "signature_date")
  private LocalDate signatureDate;

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

  public SalesDeliveryNote(String deliveryNoteNumber, SalesOrder salesOrder, Client client) {
    this.deliveryNoteNumber = deliveryNoteNumber;
    this.salesOrder = salesOrder;
    this.client = client;
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
    SalesDeliveryNote other = (SalesDeliveryNote) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum SalesDeliveryStatus {
    PREPARED,
    SHIPPED,
    DELIVERED,
    INCIDENT
  }
}
