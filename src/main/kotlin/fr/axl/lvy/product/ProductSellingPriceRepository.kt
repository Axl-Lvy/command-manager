package fr.axl.lvy.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductSellingPriceRepository : JpaRepository<ProductSellingPrice, Long> {
  fun findByProductIdAndClientId(productId: Long, clientId: Long): ProductSellingPrice?
}
