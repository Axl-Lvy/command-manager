package fr.axl.lvy.seed

import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.currency.CurrencyRepository
import fr.axl.lvy.fiscalposition.FiscalPositionRepository
import fr.axl.lvy.incoterm.IncotermRepository
import fr.axl.lvy.paymentterm.PaymentTermRepository
import fr.axl.lvy.product.ProductRepository
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
  @Autowired lateinit var currencyRepository: CurrencyRepository
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository
  @Autowired lateinit var incotermRepository: IncotermRepository
  @Autowired lateinit var fiscalPositionRepository: FiscalPositionRepository

  @Test
  fun seeder_creates_reference_data() {
    assertThat(currencyRepository.findAll()).hasSize(6)
    assertThat(paymentTermRepository.findAll()).hasSize(4)
    assertThat(incotermRepository.findAll()).hasSize(8)
    assertThat(fiscalPositionRepository.findAll()).hasSize(5)
  }

  @Test
  fun seeder_creates_users() {
    assertThat(userRepository.findAll()).hasSize(8)
  }

  @Test
  fun seeder_creates_clients() {
    assertThat(clientRepository.findAll()).hasSize(6)
  }

  @Test
  fun seeder_creates_products() {
    assertThat(productRepository.findAll()).hasSize(3)
  }
}
