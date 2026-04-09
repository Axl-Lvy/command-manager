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
class InvoiceCodigTest {

  @Autowired lateinit var invoiceCodigRepository: InvoiceCodigRepository
  @Autowired lateinit var clientRepository: ClientRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_invoice() {
    val client = createClient("CLI-IA01")
    val invoice = InvoiceCodig("FA-2026-0001", client, LocalDate.of(2026, 3, 1))
    invoice.clientName = "Acme Corp"
    invoice.notes = "Test invoice"
    invoiceCodigRepository.save(invoice)

    val found = invoiceCodigRepository.findById(invoice.id!!)
    assertThat(found).isPresent
    assertThat(found.get().invoiceNumber).isEqualTo("FA-2026-0001")
    assertThat(found.get().clientName).isEqualTo("Acme Corp")
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-IA02")
    val invoice = InvoiceCodig("FA-DEF-01", client, LocalDate.of(2026, 1, 1))

    assertThat(invoice.status).isEqualTo(InvoiceCodig.InvoiceCodigStatus.DRAFT)
    assertThat(invoice.currency).isEqualTo("EUR")
    assertThat(invoice.totalExclTax).isEqualByComparingTo("0")
    assertThat(invoice.totalVat).isEqualByComparingTo("0")
    assertThat(invoice.totalInclTax).isEqualByComparingTo("0")
    assertThat(invoice.amountPaid).isEqualByComparingTo("0")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-IA03")
    val invoice = InvoiceCodig("FA-DEL-01", client, LocalDate.of(2026, 3, 1))
    invoiceCodigRepository.save(invoice)

    assertThat(invoiceCodigRepository.findByDeletedAtIsNull()).anyMatch {
      it.invoiceNumber == "FA-DEL-01"
    }

    invoice.softDelete()
    invoiceCodigRepository.saveAndFlush(invoice)

    assertThat(invoiceCodigRepository.findByDeletedAtIsNull()).noneMatch {
      it.invoiceNumber == "FA-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-IA04")
    val invoice = InvoiceCodig("FA-REST-01", client, LocalDate.of(2026, 3, 1))
    invoiceCodigRepository.save(invoice)

    invoice.softDelete()
    assertThat(invoice.isDeleted()).isTrue

    invoice.restore()
    assertThat(invoice.isDeleted()).isFalse
    assertThat(invoice.deletedAt).isNull()
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-IA05")
    val invoice = InvoiceCodig("FA-TS-01", client, LocalDate.of(2026, 3, 1))
    invoiceCodigRepository.save(invoice)

    assertThat(invoice.createdAt).isNotNull
    assertThat(invoice.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-IA06")
    for (status in InvoiceCodig.InvoiceCodigStatus.entries) {
      val invoice = InvoiceCodig("FA-ST-${status.ordinal}", client, LocalDate.of(2026, 3, 1))
      invoice.status = status
      invoiceCodigRepository.save(invoice)

      val found = invoiceCodigRepository.findById(invoice.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun client_snapshot_fields() {
    val client = createClient("CLI-IA07")
    val invoice = InvoiceCodig("FA-SNAP-01", client, LocalDate.of(2026, 3, 1))
    invoice.clientName = "Acme Corp"
    invoice.clientAddress = "123 Main St"
    invoice.clientSiret = "12345678901234"
    invoice.clientVatNumber = "FR12345678901"
    invoiceCodigRepository.save(invoice)

    val found = invoiceCodigRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.clientName).isEqualTo("Acme Corp")
    assertThat(found.clientAddress).isEqualTo("123 Main St")
    assertThat(found.clientSiret).isEqualTo("12345678901234")
    assertThat(found.clientVatNumber).isEqualTo("FR12345678901")
  }

  @Test
  fun payment_and_legal_fields() {
    val client = createClient("CLI-IA08")
    val invoice = InvoiceCodig("FA-PAY-01", client, LocalDate.of(2026, 3, 1))
    invoice.dueDate = LocalDate.of(2026, 4, 1)
    invoice.paymentDate = LocalDate.of(2026, 3, 25)
    invoice.incoterms = "FOB"
    invoice.legalNotice = "Payment within 30 days"
    invoice.latePenalties = "3x legal rate"
    invoiceCodigRepository.save(invoice)

    val found = invoiceCodigRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.dueDate).isEqualTo(LocalDate.of(2026, 4, 1))
    assertThat(found.paymentDate).isEqualTo(LocalDate.of(2026, 3, 25))
    assertThat(found.incoterms).isEqualTo("FOB")
    assertThat(found.legalNotice).isEqualTo("Payment within 30 days")
    assertThat(found.latePenalties).isEqualTo("3x legal rate")
  }

  @Test
  fun credit_note_self_reference() {
    val client = createClient("CLI-IA09")
    val original = InvoiceCodig("FA-ORIG-01", client, LocalDate.of(2026, 3, 1))
    invoiceCodigRepository.save(original)

    val creditNote = InvoiceCodig("FA-CN-01", client, LocalDate.of(2026, 3, 15))
    creditNote.status = InvoiceCodig.InvoiceCodigStatus.CREDIT_NOTE
    creditNote.creditNote = original
    invoiceCodigRepository.save(creditNote)

    val found = invoiceCodigRepository.findById(creditNote.id!!).orElseThrow()
    assertThat(found.creditNote!!.id).isEqualTo(original.id)
  }
}
