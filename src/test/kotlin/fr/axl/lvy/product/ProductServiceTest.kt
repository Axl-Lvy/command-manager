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
  fun findDetailedById_returns_product_with_client_codes() {
    val client = createClient("CLI-DET")
    val product = Product(name = "Detailed Product")
    product.replaceClientProductCodes(listOf(client to "DET-001"))
    productService.save(product)
    productRepository.flush()

    val found = productService.findDetailedById(product.id!!)
    assertThat(found).isPresent
    assertThat(found.get().clientProductCodes).hasSize(1)
    assertThat(found.get().clientProductCodes[0].code).isEqualTo("DET-001")
  }

  @Test
  fun findDetailedById_returns_empty_for_unknown_id() {
    val found = productService.findDetailedById(-999L)
    assertThat(found).isEmpty
  }

  @Test
  fun save_generates_incremented_reference() {
    val first = Product(name = "First")
    productService.save(first)
    val firstRef = first.reference

    val second = Product(name = "Second")
    productService.save(second)

    val firstNum = firstRef.removePrefix("P").toInt()
    val secondNum = second.reference.removePrefix("P").toInt()
    assertThat(secondNum).isEqualTo(firstNum + 1)
  }

  @Test
  fun save_keeps_explicit_reference() {
    val product = Product("CUSTOM-REF", "Custom")
    productService.save(product)

    assertThat(product.reference).isEqualTo("CUSTOM-REF")
  }

  @Test
  fun delete_nonexistent_id_does_not_throw() {
    productService.delete(-999L)
    // No exception expected
  }

  @Test
  fun replaceClientProductCodes_clears_previous_codes() {
    val clientA = createClient("CLI-R01")
    val clientB = createClient("CLI-R02")
    val product = Product(name = "Replaceable")
    product.replaceClientProductCodes(listOf(clientA to "OLD-001"))
    productService.save(product)
    productRepository.flush()

    product.replaceClientProductCodes(listOf(clientB to "NEW-002"))
    productService.save(product)
    productRepository.flush()

    val found = productService.findById(product.id!!).orElseThrow()
    assertThat(found.findClientProductCode(clientA)).isNull()
    assertThat(found.findClientProductCode(clientB)).isEqualTo("NEW-002")
  }

  @Test
  fun findClientProductCode_returns_null_for_null_client() {
    val product = Product("REF-NUL", "No Client")
    product.replaceClientProductCodes(listOf(createClient("CLI-X") to "X-001"))
    productService.save(product)

    assertThat(product.findClientProductCode(null)).isNull()
  }

  @Test
  fun validateOnUpdate_resets_mto_for_service() {
    val product = Product("REF-UPD", "Service Update")
    product.type = Product.ProductType.PRODUCT
    product.mto = true
    productService.save(product)
    productRepository.flush()

    val found = productService.findById(product.id!!).orElseThrow()
    assertThat(found.mto).isTrue

    found.type = Product.ProductType.SERVICE
    productService.save(found)
    productRepository.flush()

    val updated = productService.findById(product.id!!).orElseThrow()
    assertThat(updated.mto).isFalse
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
