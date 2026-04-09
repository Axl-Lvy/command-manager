package fr.axl.lvy.paymentterm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class PaymentTermServiceTest {

  @Autowired lateinit var paymentTermService: PaymentTermService
  @Autowired lateinit var paymentTermRepository: PaymentTermRepository

  @Test
  fun save_normalizes_and_persists_payment_term() {
    val paymentTerm = PaymentTerm(" 30 jours fin de mois ")

    val saved = paymentTermService.save(paymentTerm)

    assertThat(saved.label).isEqualTo("30 jours fin de mois")
  }

  @Test
  fun save_rejects_duplicate_label_case_insensitive() {
    paymentTermService.save(PaymentTerm("30 jours"))

    assertThatThrownBy { paymentTermService.save(PaymentTerm("30 JOURS")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("30 JOURS")
  }

  @Test
  fun findAll_returns_payment_terms_sorted_by_label() {
    paymentTermService.save(PaymentTerm("60 jours"))
    paymentTermService.save(PaymentTerm("30 jours"))

    val paymentTerms = paymentTermService.findAll()

    assertThat(paymentTerms.map { it.label }).containsExactly("30 jours", "60 jours")
  }

  @Test
  fun delete_removes_payment_term() {
    val paymentTerm = paymentTermService.save(PaymentTerm("45 jours"))

    paymentTermService.delete(paymentTerm.id!!)
    paymentTermRepository.flush()

    assertThat(paymentTermService.findById(paymentTerm.id!!)).isEmpty
  }
}
