package fr.axl.lvy.currency

import java.util.Optional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages the currency reference table. Enforces code uniqueness (case-insensitive). */
@Service
class CurrencyService(private val currencyRepository: CurrencyRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<Currency> = currencyRepository.findAllByOrderByCodeAsc()

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Currency> = currencyRepository.findById(id)

  @Transactional
  fun save(currency: Currency): Currency {
    currency.code = currency.code.trim().uppercase()
    currency.symbol = currency.symbol.trim()
    currency.name = currency.name.trim()

    val existing = currencyRepository.findByCodeIgnoreCase(currency.code)
    require(!(existing.isPresent && existing.get().id != currency.id)) {
      "Une devise avec le code '${currency.code}' existe déjà"
    }
    return currencyRepository.save(currency)
  }

  @Transactional
  fun delete(id: Long) {
    if (currencyRepository.existsById(id)) {
      currencyRepository.deleteById(id)
    }
  }
}
