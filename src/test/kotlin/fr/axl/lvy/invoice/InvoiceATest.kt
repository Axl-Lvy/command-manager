package fr.axl.lvy.invoice

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class InvoiceATest {

  @Autowired lateinit var invoiceARepository: InvoiceARepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_invoice() {
    val client = createClient("CLI-IA01")
    val invoice = InvoiceA("FA-2026-0001", client, LocalDate.of(2026, 3, 1))
    invoice.clientName = "Acme Corp"
    invoice.notes = "Test invoice"
    invoiceARepository.save(invoice)

    val found = invoiceARepository.findById(invoice.id!!)
    assertThat(found).isPresent
    assertThat(found.get().invoiceNumber).isEqualTo("FA-2026-0001")
    assertThat(found.get().clientName).isEqualTo("Acme Corp")
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-IA02")
    val invoice = InvoiceA("FA-DEF-01", client, LocalDate.of(2026, 1, 1))

    assertThat(invoice.status).isEqualTo(InvoiceA.InvoiceAStatus.DRAFT)
    assertThat(invoice.currency).isEqualTo("EUR")
    assertThat(invoice.totalExclTax).isEqualByComparingTo("0")
    assertThat(invoice.totalVat).isEqualByComparingTo("0")
    assertThat(invoice.totalInclTax).isEqualByComparingTo("0")
    assertThat(invoice.amountPaid).isEqualByComparingTo("0")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-IA03")
    val invoice = InvoiceA("FA-DEL-01", client, LocalDate.of(2026, 3, 1))
    invoiceARepository.save(invoice)

    assertThat(invoiceARepository.findByDeletedAtIsNull()).anyMatch {
      it.invoiceNumber == "FA-DEL-01"
    }

    invoice.softDelete()
    invoiceARepository.saveAndFlush(invoice)

    assertThat(invoiceARepository.findByDeletedAtIsNull()).noneMatch {
      it.invoiceNumber == "FA-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-IA04")
    val invoice = InvoiceA("FA-REST-01", client, LocalDate.of(2026, 3, 1))
    invoiceARepository.save(invoice)

    invoice.softDelete()
    assertThat(invoice.isDeleted()).isTrue

    invoice.restore()
    assertThat(invoice.isDeleted()).isFalse
    assertThat(invoice.deletedAt).isNull()
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-IA05")
    val invoice = InvoiceA("FA-TS-01", client, LocalDate.of(2026, 3, 1))
    invoiceARepository.save(invoice)

    assertThat(invoice.createdAt).isNotNull
    assertThat(invoice.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-IA06")
    for (status in InvoiceA.InvoiceAStatus.entries) {
      val invoice = InvoiceA("FA-ST-${status.ordinal}", client, LocalDate.of(2026, 3, 1))
      invoice.status = status
      invoiceARepository.save(invoice)

      val found = invoiceARepository.findById(invoice.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun client_snapshot_fields() {
    val client = createClient("CLI-IA07")
    val invoice = InvoiceA("FA-SNAP-01", client, LocalDate.of(2026, 3, 1))
    invoice.clientName = "Acme Corp"
    invoice.clientAddress = "123 Main St"
    invoice.clientSiret = "12345678901234"
    invoice.clientVatNumber = "FR12345678901"
    invoiceARepository.save(invoice)

    val found = invoiceARepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.clientName).isEqualTo("Acme Corp")
    assertThat(found.clientAddress).isEqualTo("123 Main St")
    assertThat(found.clientSiret).isEqualTo("12345678901234")
    assertThat(found.clientVatNumber).isEqualTo("FR12345678901")
  }
}
