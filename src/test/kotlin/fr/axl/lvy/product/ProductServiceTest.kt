package fr.axl.lvy.product

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ProductServiceTest {

  @Autowired lateinit var productService: ProductService
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_product() {
    val product = Product(name = "Steel Beam")
    product.sellingPriceExclTax = BigDecimal("150.00")
    product.purchasePriceExclTax = BigDecimal("80.00")
    product.vatRate = BigDecimal("20.00")
    product.unit = "kg"
    productService.save(product)

    val found = productService.findById(product.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Steel Beam")
    assertThat(found.get().reference).matches("""P\d{6}""")
    assertThat(found.get().sellingPriceExclTax).isEqualByComparingTo("150.00")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val product = Product("REF-DEL", "To Delete")
    productService.save(product)
    assertThat(productService.findAll()).anyMatch { it.reference == "REF-DEL" }

    productService.delete(product.id!!)
    productRepository.flush()

    assertThat(productService.findAll()).noneMatch { it.reference == "REF-DEL" }
    // Still exists in DB
    assertThat(productService.findById(product.id!!)).isPresent
    assertThat(productService.findById(product.id!!).get().isDeleted()).isTrue
  }

  @Test
  fun findActive_excludes_inactive_products() {
    val active = Product("REF-ACT", "Active Product")
    active.active = true
    productService.save(active)

    val inactive = Product("REF-INA", "Inactive Product")
    inactive.active = false
    productService.save(inactive)

    val activeProducts = productService.findActive()
    assertThat(activeProducts)
      .anyMatch { it.reference == "REF-ACT" }
      .noneMatch { it.reference == "REF-INA" }
  }

  @Test
  fun service_product_cannot_be_mto() {
    val product = Product("REF-SVC", "Consulting")
    product.type = Product.ProductType.SERVICE
    product.mto = true
    productService.save(product)
    productRepository.flush()

    val found = productService.findById(product.id!!).orElseThrow()
    assertThat(found.mto).isFalse
  }

  @Test
  fun product_can_be_mto() {
    val product = Product("REF-MTO", "Custom Part")
    product.type = Product.ProductType.PRODUCT
    product.mto = true
    productService.save(product)
    productRepository.flush()

    val found = productService.findById(product.id!!).orElseThrow()
    assertThat(found.mto).isTrue
  }

  @Test
  fun product_has_timestamps_after_persist() {
    val product = Product(name = "Timestamped")
    productService.save(product)

    assertThat(product.createdAt).isNotNull
    assertThat(product.updatedAt).isNotNull
  }

  @Test
  fun save_persists_client_product_codes_per_client() {
    val clientA = createClient("CLI-P01")
    val clientB = createClient("CLI-P02")
    val product = Product(name = "Beam")
    product.replaceClientProductCodes(listOf(clientA to "A-001", clientB to "B-002"))

    productService.save(product)
    productRepository.flush()

    val found = productService.findById(product.id!!).orElseThrow()
    assertThat(found.findClientProductCode(clientA)).isEqualTo("A-001")
    assertThat(found.findClientProductCode(clientB)).isEqualTo("B-002")
  }
}
