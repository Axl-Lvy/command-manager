package fr.axl.lvy.client.contact;

import fr.axl.lvy.client.Client;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
public class Contact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(lombok.AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @NotBlank
  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Nullable
  @Column(name = "first_name")
  private String firstName;

  @Nullable private String email;

  @Nullable
  @Column(length = 30)
  private String phone;

  @Nullable
  @Column(length = 30)
  private String mobile;

  @Nullable
  @Column(name = "job_title", length = 100)
  private String jobTitle;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ContactRole role = ContactRole.OTHER;

  private boolean active = true;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Setter(lombok.AccessLevel.NONE)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Contact(Client client, String lastName) {
    this.client = client;
    this.lastName = lastName;
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !getClass().isAssignableFrom(obj.getClass())) return false;
    Contact other = (Contact) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  public enum ContactRole {
    PRIMARY,
    BILLING,
    TECHNICAL,
    OTHER
  }
}
