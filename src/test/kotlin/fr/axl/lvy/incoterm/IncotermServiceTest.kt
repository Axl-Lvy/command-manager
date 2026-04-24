package fr.axl.lvy.incoterm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
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
  fun save_rejects_duplicate_name() {
    incotermService.save(Incoterm(name = "exw", label = "Ex Works"))

    assertThatThrownBy { incotermService.save(Incoterm(name = "EXW", label = "Ex Works (dup)")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("EXW")
  }

  @Test
  fun save_allows_updating_existing_incoterm() {
    val incoterm = incotermService.save(Incoterm(name = "cif", label = "Cost Insurance Freight"))
    incoterm.label = "Updated Label"
    val updated = incotermService.save(incoterm)
    assertThat(updated.label).isEqualTo("Updated Label")
  }

  @Test
  fun findAll_paginated_returns_all_incoterms() {
    incotermService.save(Incoterm(name = "ddp", label = "Delivered Duty Paid"))
    incotermService.save(Incoterm(name = "fca", label = "Free Carrier"))

    val page = incotermService.findAll(PageRequest.of(0, 100))

    assertThat(page.content.map { it.name }).contains("DDP", "FCA")
  }

  @Test
  fun delete_removes_incoterm() {
    val incoterm = incotermService.save(Incoterm(name = "dap", label = "Delivered At Place"))

    incotermService.delete(incoterm.id!!)
    incotermRepository.flush()

    assertThat(incotermService.findById(incoterm.id!!)).isEmpty
  }
}
