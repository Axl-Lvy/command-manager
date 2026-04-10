package fr.axl.lvy.base.ui

import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.incoterm.Incoterm

/**
 * Loads the detailed client (with incoterm eagerly fetched) and applies its default address and
 * incoterm values to the given form fields. Returns the detailed client so callers can read
 * additional fields (e.g. [Client.deliveryPort]).
 */
fun loadAndApplyClientDefaults(
  client: Client,
  clientService: ClientService,
  billingAddress: TextArea,
  shippingAddress: TextArea,
  incotermCombo: ComboBox<Incoterm>,
  incotermLocation: TextField,
  allIncoterms: List<Incoterm>,
): Client {
  val detailed = client.id?.let { clientService.findDetailedById(it).orElse(client) } ?: client
  billingAddress.value = detailed.billingAddress ?: ""
  shippingAddress.value = detailed.shippingAddress ?: ""
  incotermCombo.value = allIncoterms.firstOrNull { it.id == detailed.incoterm?.id }
  incotermLocation.value = detailed.incotermLocation ?: ""
  return detailed
}
