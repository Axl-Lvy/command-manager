package fr.axl.lvy.seed

import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.currency.CurrencyRepository
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.fiscalposition.FiscalPositionRepository
import fr.axl.lvy.incoterm.IncotermRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.paymentterm.PaymentTermRepository
import fr.axl.lvy.product.ProductRepository
import fr.axl.lvy.sale.SalesCodigRepository
import fr.axl.lvy.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("seed")
class DatabaseSeederTest {

  @Autowired lateinit var userRepository: UserRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var salesCodigRepository: SalesCodigRepository
  @Autowired lateinit var currencyRepository: CurrencyRepository
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var incotermRepository: IncotermRepository
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository
  @Autowired lateinit var documentLineRepository: DocumentLineRepository

  @Test
  fun seeder_creates_reference_data() {
    assertThat(currencyRepository.findAll()).hasSize(3)
    assertThat(paymentTermRepository.findAll()).hasSize(4)
    assertThat(incotermRepository.findAll()).hasSize(5)
    assertThat(fiscalPositionRepository.findAll()).hasSize(3)
  }

  @Test
  fun seeder_creates_users() {
    assertThat(userRepository.findAll()).hasSize(3)
  }

  @Test
  fun seeder_creates_clients() {
    assertThat(clientRepository.findAll()).hasSize(4)
  }

  @Test
  fun seeder_creates_products() {
    assertThat(productRepository.findAll()).hasSize(5)
  }

  @Test
  fun seeder_creates_orders_codig_with_correct_statuses() {
    val orders = orderCodigRepository.findAll()
    assertThat(orders).hasSizeGreaterThanOrEqualTo(2)
    assertThat(orders.map { it.status })
      .contains(OrderCodig.OrderCodigStatus.DRAFT, OrderCodig.OrderCodigStatus.CONFIRMED)
  }

  @Test
  fun seeder_creates_sales_codig() {
    assertThat(salesCodigRepository.findAll()).hasSizeGreaterThanOrEqualTo(2)
  }

  @Test
  fun seeder_creates_document_lines() {
    assertThat(documentLineRepository.findAll()).isNotEmpty()
  }
}
