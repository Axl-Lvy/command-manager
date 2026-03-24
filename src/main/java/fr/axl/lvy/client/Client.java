package fr.axl.lvy.client;

import fr.axl.lvy.client.contact.Contact;
import fr.axl.lvy.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @NotBlank
  @Column(name = "client_code", nullable = false, unique = true, length = 20)
  private String clientCode;

  @NotBlank
  @Column(nullable = false)
  private String name;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientType type = ClientType.COMPANY;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientRole role = ClientRole.CLIENT;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "visible_company", nullable = false)
  private User.Company visibleCompany = User.Company.A;

  @Nullable private String email;

  @Nullable
  @Column(length = 30)
  private String phone;

  @Nullable private String website;

  @Nullable
  @Column(length = 20)
  private String siret;

  @Nullable
  @Column(name = "vat_number", length = 20)
  private String vatNumber;

  @Nullable
  @Column(name = "billing_address", columnDefinition = "TEXT")
  private String billingAddress;

  @Nullable
  @Column(name = "shipping_address", columnDefinition = "TEXT")
  private String shippingAddress;

  @Nullable
  @Column(name = "payment_delay")
  private Integer paymentDelay;

  @Nullable
  @Column(name = "payment_method", length = 50)
  private String paymentMethod;

  @NotNull
  @Column(name = "default_discount", nullable = false, precision = 5, scale = 2)
  private BigDecimal defaultDiscount = BigDecimal.ZERO;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.ACTIVE;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String notes;

  @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Contact> contacts = new ArrayList<>();

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

  public Client(String clientCode, String name) {
    this.clientCode = clientCode;
    this.name = name;
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

  public boolean isClient() {
    return role == ClientRole.CLIENT || role == ClientRole.BOTH;
  }

  public boolean isProducer() {
    return role == ClientRole.PRODUCER || role == ClientRole.BOTH;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !getClass().isAssignableFrom(obj.getClass())) return false;
    Client other = (Client) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum ClientType {
    COMPANY,
    INDIVIDUAL
  }

  public enum ClientRole {
    CLIENT,
    PRODUCER,
    BOTH
  }

  public enum Status {
    ACTIVE,
    INACTIVE
  }
}
