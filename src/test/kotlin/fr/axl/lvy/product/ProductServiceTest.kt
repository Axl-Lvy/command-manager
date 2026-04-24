package fr.axl.lvy.product

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ProductServiceTest {

  @Autowired lateinit var productService: ProductService
  @Autowired lateinit var productRepository: ProductRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var entityManager: EntityManager

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_product() {
    val product = Product(name = "Steel Beam")
    product.label = "Steel beam EN10025"
    product.shortDescription = "Short beam description"
    product.longDescription = "Long beam description with full commercial details."
    product.sellingPriceExclTax = BigDecimal("150.00")
    product.sellingCurrency = "USD"
    product.purchasePriceExclTax = BigDecimal("80.00")
    product.purchaseCurrency = "CNY"
    product.priceType = "Prix départ usine"
    product.unit = "kg"
    productService.save(product)

    val found = productService.findById(product.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Steel Beam")
    assertThat(found.get().label).isEqualTo("Steel beam EN10025")
    assertThat(found.get().shortDescription).isEqualTo("Short beam description")
    assertThat(found.get().longDescription)
      .isEqualTo("Long beam description with full commercial details.")
    assertThat(found.get().reference).matches("""P\d{6}""")
    assertThat(found.get().sellingPriceExclTax).isEqualByComparingTo("150.00")
    assertThat(found.get().sellingCurrency).isEqualTo("USD")
    assertThat(found.get().purchaseCurrency).isEqualTo("CNY")
    assertThat(found.get().priceType).isEqualTo("Prix départ usine")
  }

  @Test
  fun findAll_paginated_excludes_soft_deleted() {
    val keep = Product("REF-PAGE-KEEP", "Keep Me")
    productService.save(keep)
    val gone = Product("REF-PAGE-GONE", "Remove Me")
    productService.save(gone)
    productService.delete(gone.id!!)
    productRepository.flush()

    val page = productService.findAll(PageRequest.of(0, 100))
    assertThat(page.content).anyMatch { it.reference == "REF-PAGE-KEEP" }
    assertThat(page.content).noneMatch { it.reference == "REF-PAGE-GONE" }
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
  fun findActive_loads_client_product_codes() {
    val client = createClient("CLI-ACT")
    val product = Product("REF-CODE", "Product With Client Code")
    product.replaceClientProductCodes(listOf(client to "C-ACT-001"))
    productService.save(product)
    productRepository.flush()

    val activeProduct =
      productService.findActive().firstOrNull { it.reference == "REF-CODE" } ?: error("not found")

    assertThat(activeProduct.findClientProductCode(client)).isEqualTo("C-ACT-001")
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
  fun default_price_type_is_blank() {
    val product = Product("REF-PRICE-TYPE", "Price Type Product")

    assertThat(product.priceType).isNull()
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
  fun isMtoProduct_returns_false_for_service_even_if_flag_is_true() {
    val product = Product("REF-SVC-MTO", "Consulting")
    product.type = Product.ProductType.SERVICE
    product.mto = true

    assertThat(product.isMtoProduct()).isFalse()
  }

  @Test
  fun isMtoProduct_returns_true_only_for_mto_products() {
    val product = Product("REF-PROD-MTO", "Custom Part")
    product.type = Product.ProductType.PRODUCT
    product.mto = true

    assertThat(product.isMtoProduct()).isTrue()
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
  fun replaceClientProductCodes_updates_existing_client_code_without_duplicate_insert() {
    val client = createClient("CLI-R03")
    val product = Product(name = "Code Update")
    product.replaceClientProductCodes(listOf(client to "OLD-001"))
    productService.save(product)
    productRepository.flush()
    entityManager.clear()

    val loaded = productService.findDetailedById(product.id!!).orElseThrow()
    loaded.replaceClientProductCodes(listOf(loaded.clientProductCodes.first().client to "NEW-001"))
    productService.save(loaded)
    productRepository.flush()
    entityManager.clear()

    val found = productService.findDetailedById(product.id!!).orElseThrow()
    assertThat(found.clientProductCodes).hasSize(1)
    assertThat(found.clientProductCodes.first().code).isEqualTo("NEW-001")
  }

  @Test
  fun findClientProductCode_returns_null_for_null_client() {
    val product = Product("REF-NUL", "No Client")
    product.replaceClientProductCodes(listOf(createClient("CLI-X") to "X-001"))
    productService.save(product)

    assertThat(product.findClientProductCode(null)).isNull()
  }

  @Test
  fun service_findClientProductCode_returns_code_when_present_and_null_when_absent() {
    val client = createClient("CLI-SVC-PC")
    val other = createClient("CLI-SVC-OTHER")
    val product = Product(name = "Svc Product")
    product.replaceClientProductCodes(listOf(client to "SVC-001"))
    productService.save(product)
    productRepository.flush()

    assertThat(productService.findClientProductCode(product.id!!, client.id!!)).isEqualTo("SVC-001")
    assertThat(productService.findClientProductCode(product.id!!, other.id!!)).isNull()
  }

  @Test
  fun service_findFirstClientProductCode_returns_first_or_null() {
    val client = createClient("CLI-FIRST")
    val productWithCode = Product(name = "With Code")
    productWithCode.replaceClientProductCodes(listOf(client to "FIRST-001"))
    productService.save(productWithCode)

    val productWithout = Product(name = "Without Code")
    productService.save(productWithout)
    productRepository.flush()

    assertThat(productService.findFirstClientProductCode(productWithCode.id!!))
      .isEqualTo("FIRST-001")
    assertThat(productService.findFirstClientProductCode(productWithout.id!!)).isNull()
  }

  @Test
  fun archive_and_delete_execute_without_error() {
    val product = productService.save(Product(name = "To Archive"))
    productRepository.flush()

    productService.archive(product.id!!)
    productService.delete(product.id!!)
  }

  @Test
  fun archive_and_delete_ignore_unknown_ids() {
    productService.archive(-99L)
    productService.delete(-99L)
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

  @Test
  fun save_persists_multiple_suppliers() {
    val supplierA = createClient("CLI-SUP1").apply { role = Client.ClientRole.PRODUCER }
    val supplierB = createClient("CLI-SUP2").apply { role = Client.ClientRole.BOTH }
    clientRepository.save(supplierA)
    clientRepository.save(supplierB)
    val product = Product(name = "Supplier Product")
    product.replaceSuppliers(listOf(supplierA, supplierB))

    productService.save(product)
    productRepository.flush()

    val found = productService.findDetailedById(product.id!!).orElseThrow()
    assertThat(found.suppliers.map { it.clientCode })
      .containsExactlyInAnyOrder("CLI-SUP1", "CLI-SUP2")
  }

  @Test
  fun save_accepts_detached_client_for_client_product_code() {
    val client = createClient("CLI-DETACHED")
    entityManager.flush()
    entityManager.clear()

    val detachedClient = clientRepository.findById(client.id!!).orElseThrow()
    entityManager.clear()

    val product = Product(name = "Detached Client Product")
    product.replaceClientProductCodes(listOf(detachedClient to "DET-CLIENT-001"))

    productService.save(product)
    productRepository.flush()

    val found = productService.findDetailedById(product.id!!).orElseThrow()
    assertThat(found.findClientProductCode(found.clientProductCodes.first().client))
      .isEqualTo("DET-CLIENT-001")
  }
}
