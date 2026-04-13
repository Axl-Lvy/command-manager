package fr.axl.lvy.seed

import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.currency.CurrencyRepository
import fr.axl.lvy.delivery.DeliveryNoteCodigRepository
import fr.axl.lvy.delivery.DeliveryNoteNetstoneRepository
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.fiscalposition.FiscalPositionRepository
import fr.axl.lvy.incoterm.IncotermRepository
import fr.axl.lvy.invoice.InvoiceCodigRepository
import fr.axl.lvy.invoice.InvoiceNetstoneRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderNetstoneRepository
import fr.axl.lvy.paymentterm.PaymentTermRepository
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.sale.SalesNetstoneRepository
import fr.axl.lvy.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DatabaseSeederTest {

  @Autowired lateinit var userRepository: UserRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var salesNetstoneRepository: SalesNetstoneRepository
  @Autowired lateinit var currencyRepository: CurrencyRepository
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var incotermRepository: IncotermRepository
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository
  @Autowired lateinit var deliveryNoteCodigRepository: DeliveryNoteCodigRepository
  @Autowired lateinit var deliveryNoteNetstoneRepository: DeliveryNoteNetstoneRepository
  @Autowired lateinit var invoiceCodigRepository: InvoiceCodigRepository
  @Autowired lateinit var invoiceNetstoneRepository: InvoiceNetstoneRepository

  @Test
  fun seeder_creates_reference_data() {
    assertThat(currencyRepository.findAll()).hasSize(6)
    assertThat(paymentTermRepository.findAll()).hasSize(7)
    assertThat(incotermRepository.findAll()).hasSize(8)
    assertThat(fiscalPositionRepository.findAll()).hasSize(5)
  }

  @Test
  fun seeder_creates_users() {
    assertThat(userRepository.findAll()).hasSize(8)
  }

  @Test
  fun seeder_creates_clients() {
    // 14 clients: 12 base + Codig OWN_COMPANY + Netstone OWN_COMPANY (added for MTO chain)
    assertThat(clientRepository.findAll()).hasSize(14)
  }

  @Test
  fun seeder_creates_products() {
    assertThat(productRepository.findAll()).hasSize(14)
  }

  @Test
  fun seeder_creates_orders_codig_with_all_statuses() {
    val orders = orderCodigRepository.findAll()
    // 20 direct + 3 auto-generated DRAFT orders from VALIDATED MTO sales
    assertThat(orders).hasSize(23)
    val statuses = orders.map { it.status }.toSet()
    assertThat(statuses)
      .containsExactlyInAnyOrder(
        OrderCodig.OrderCodigStatus.DRAFT,
        OrderCodig.OrderCodigStatus.CONFIRMED,
        OrderCodig.OrderCodigStatus.DELIVERED,
        OrderCodig.OrderCodigStatus.INVOICED,
        OrderCodig.OrderCodigStatus.CANCELLED,
      )
  }

  @Test
  fun seeder_creates_expected_order_codig_status_counts() {
    val orders = orderCodigRepository.findAll()
    val byStatus = orders.groupBy { it.status }
    // 3 explicit DRAFT + 3 auto-generated from MTO VALIDATED sales
    assertThat(byStatus[OrderCodig.OrderCodigStatus.DRAFT]).hasSize(6)
    // 5 regular confirmed + 3 MTO confirmed (returned for Netstone seeding)
    assertThat(byStatus[OrderCodig.OrderCodigStatus.CONFIRMED]).hasSize(8)
    assertThat(byStatus[OrderCodig.OrderCodigStatus.DELIVERED]).hasSize(4)
    assertThat(byStatus[OrderCodig.OrderCodigStatus.INVOICED]).hasSize(4)
    assertThat(byStatus[OrderCodig.OrderCodigStatus.CANCELLED]).hasSize(1)
  }

  @Test
  fun seeder_creates_orders_netstone() {
    assertThat(orderNetstoneRepository.findAll()).hasSize(3)
  }

  @Test
  fun seeder_creates_sales_codig() {
    // 4 DRAFT + 3 VALIDATED non-MTO + 3 VALIDATED MTO + 1 CANCELLED
    assertThat(salesCodigRepository.findAll()).hasSize(11)
  }

  @Test
  fun seeder_creates_delivery_notes_codig() {
    // 4 for DELIVERED orders + 4 for INVOICED orders
    assertThat(deliveryNoteCodigRepository.findAll()).hasSize(8)
  }

  @Test
  fun seeder_creates_delivery_notes_netstone() {
    assertThat(deliveryNoteNetstoneRepository.findAll()).hasSize(2)
  }

  @Test
  fun seeder_creates_invoices_codig() {
    assertThat(invoiceCodigRepository.findAll()).hasSize(4)
  }

  @Test
  fun seeder_creates_invoices_netstone() {
    // 1 order-linked (paid) + 1 standalone (verified) + 1 disputed
    assertThat(invoiceNetstoneRepository.findAll()).hasSize(3)
  }

  @Test
  fun seeder_creates_document_lines() {
    assertThat(documentLineRepository.findAll()).isNotEmpty()
  }
}
