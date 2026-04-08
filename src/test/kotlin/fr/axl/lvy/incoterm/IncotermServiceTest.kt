package fr.axl.lvy.incoterm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class IncotermServiceTest {

  @Autowired lateinit var incotermService: IncotermService
  @Autowired lateinit var incotermRepository: IncotermRepository

  @Test
  fun save_and_retrieve_incoterm() {
    val incoterm = Incoterm(name = "cfr", label = "Cost and Freight")
    incotermService.save(incoterm)

    val found = incotermService.findById(incoterm.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("CFR")
    assertThat(found.get().label).isEqualTo("Cost and Freight")
  }

  @Test
  fun findAll_returns_incoterms_sorted_by_name() {
    incotermService.save(Incoterm(name = "fob", label = "Free On Board"))
    incotermService.save(Incoterm(name = "cfr", label = "Cost and Freight"))

    val found = incotermService.findAll()

    assertThat(found.map { it.name }).containsExactly("CFR", "FOB")
  }

  @Test
  fun delete_removes_incoterm() {
    val incoterm = incotermService.save(Incoterm(name = "dap", label = "Delivered At Place"))

    incotermService.delete(incoterm.id!!)
    incotermRepository.flush()

    assertThat(incotermService.findById(incoterm.id!!)).isEmpty
  }
}
