package fr.axl.lvy.currency

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class CurrencyServiceTest {

  @Autowired lateinit var currencyService: CurrencyService

  @Test
  fun save_normalizes_and_persists_currency() {
    val currency = Currency(" eur ", " € ", " Euro ")

    val saved = currencyService.save(currency)

    assertThat(saved.code).isEqualTo("EUR")
    assertThat(saved.symbol).isEqualTo("€")
    assertThat(saved.name).isEqualTo("Euro")
  }

  @Test
  fun save_rejects_duplicate_code_case_insensitive() {
    currencyService.save(Currency("EUR", "€", "Euro"))

    assertThatThrownBy { currencyService.save(Currency("eur", "$", "Duplicate")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("EUR")
  }

  @Test
  fun findAll_returns_currencies_sorted_by_code() {
    currencyService.save(Currency("USD", "$", "US Dollar"))
    currencyService.save(Currency("EUR", "€", "Euro"))

    val currencies = currencyService.findAll()

    assertThat(currencies.map { it.code }).containsExactly("EUR", "USD")
  }
}
