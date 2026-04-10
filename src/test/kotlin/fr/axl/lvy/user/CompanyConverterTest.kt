package fr.axl.lvy.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompanyConverterTest {

  private val converter = CompanyConverter()

  @Test
  fun convertToEntityAttribute_accepts_legacy_company_values() {
    assertThat(converter.convertToEntityAttribute("A")).isEqualTo(User.Company.CODIG)
    assertThat(converter.convertToEntityAttribute("B")).isEqualTo(User.Company.NETSTONE)
    assertThat(converter.convertToEntityAttribute("AB")).isEqualTo(User.Company.BOTH)
  }

  @Test
  fun convertToEntityAttribute_accepts_current_company_values() {
    assertThat(converter.convertToEntityAttribute("CODIG")).isEqualTo(User.Company.CODIG)
    assertThat(converter.convertToEntityAttribute("NETSTONE")).isEqualTo(User.Company.NETSTONE)
    assertThat(converter.convertToEntityAttribute("BOTH")).isEqualTo(User.Company.BOTH)
  }
}
