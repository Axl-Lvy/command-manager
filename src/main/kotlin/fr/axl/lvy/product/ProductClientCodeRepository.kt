package fr.axl.lvy.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductClientCodeRepository : JpaRepository<ProductClientCode, Long> {

  @Query(
    """
      SELECT pcc.code
      FROM ProductClientCode pcc
      WHERE pcc.product.id = :productId
        AND pcc.client.id = :clientId
    """
  )
  fun findCodeByProductIdAndClientId(productId: Long, clientId: Long): String?

  @Query(
    """
      SELECT pcc.code
      FROM ProductClientCode pcc
      WHERE pcc.product.id = :productId
      ORDER BY pcc.id
    """
  )
  fun findCodesByProductId(productId: Long): List<String>
}
