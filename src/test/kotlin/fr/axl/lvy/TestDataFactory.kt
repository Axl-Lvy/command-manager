package fr.axl.lvy

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class TestDataFactory(
  private val clientRepository: ClientRepository,
  private val documentLineRepository: DocumentLineRepository,
  private val productRepository: ProductRepository,
) {

  fun createClient(
    code: String,
    billingAddress: String? = null,
    shippingAddress: String? = null,
  ): Client {
    val client = Client(code, "Client $code")
    client.billingAddress = billingAddress
    client.shippingAddress = shippingAddress
    return clientRepository.save(client)
  }

  fun createDocumentLine(
    type: DocumentLine.DocumentType,
    documentId: Long,
    designation: String,
    quantity: BigDecimal = BigDecimal.ONE,
    unitPrice: BigDecimal = BigDecimal("100.00"),
    vatRate: BigDecimal = BigDecimal("20.00"),
    product: Product? = null,
  ): DocumentLine {
    val line = DocumentLine(type, documentId, designation)
    line.product = product
    line.quantity = quantity
    line.unitPriceExclTax = unitPrice
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = vatRate
    line.position = 0
    line.recalculate()
    return documentLineRepository.saveAndFlush(line)
  }

  fun createMtoProduct(ref: String): Product {
    val product = Product(ref, "MTO $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = true
    product.sellingPriceExclTax = BigDecimal("100.00")
    product.purchasePriceExclTax = BigDecimal("60.00")
    return productRepository.saveAndFlush(product)
  }

  fun createRegularProduct(ref: String): Product {
    val product = Product(ref, "Regular $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = false
    product.sellingPriceExclTax = BigDecimal("50.00")
    product.purchasePriceExclTax = BigDecimal("30.00")
    return productRepository.saveAndFlush(product)
  }
}
