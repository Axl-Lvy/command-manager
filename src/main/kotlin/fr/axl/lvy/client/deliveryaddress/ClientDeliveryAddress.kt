package fr.axl.lvy.client.deliveryaddress

import fr.axl.lvy.base.BaseEntity
import fr.axl.lvy.client.Client
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** A named delivery address for a [Client], selectable on sales documents. */
@Entity
@Table(name = "client_delivery_addresses")
class ClientDeliveryAddress(@Column(nullable = false, length = 100) var label: String) :
  BaseEntity() {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  lateinit var client: Client

  @Column(name = "address", nullable = false, columnDefinition = "TEXT") var address: String = ""

  @Column(name = "is_default", nullable = false) var defaultAddress: Boolean = false

  constructor(client: Client, label: String, address: String) : this(label) {
    this.client = client
    this.address = address
  }
}
