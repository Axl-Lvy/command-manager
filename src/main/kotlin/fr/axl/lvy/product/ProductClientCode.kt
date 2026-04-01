package fr.axl.lvy.product

import fr.axl.lvy.base.BaseEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
  name = "product_client_codes",
  uniqueConstraints = [UniqueConstraint(columnNames = ["product_id", "client_id"])],
)
class ProductClientCode(
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  var product: Product,
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  var client: Client,
  @NotBlank @Column(name = "code", nullable = false, length = 100) var code: String,
) : BaseEntity()
