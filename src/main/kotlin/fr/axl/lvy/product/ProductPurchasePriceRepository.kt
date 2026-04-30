package fr.axl.lvy.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductPurchasePriceRepository : JpaRepository<ProductPurchasePrice, Long> {
  fun findByProductIdAndCompany(productId: Long, company: ProductPriceCompany): ProductPurchasePrice?
}
