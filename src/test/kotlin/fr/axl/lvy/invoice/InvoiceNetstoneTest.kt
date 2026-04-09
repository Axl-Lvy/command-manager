package fr.axl.lvy.invoice

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class InvoiceNetstoneTest {

  @Autowired lateinit var invoiceNetstoneRepository: InvoiceNetstoneRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var orderCodigRepository: OrderCodigRepository
  @Autowired lateinit var orderNetstoneRepository: OrderNetstoneRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_invoice() {
    val client = createClient("CLI-IB01")
    val invoice =
      InvoiceNetstone(
        "FB-2026-0001",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoice.supplierInvoiceNumber = "SUP-001"
    invoiceNetstoneRepository.save(invoice)

    val found = invoiceNetstoneRepository.findById(invoice.id!!)
    assertThat(found).isPresent
    assertThat(found.get().internalInvoiceNumber).isEqualTo("FB-2026-0001")
    assertThat(found.get().supplierInvoiceNumber).isEqualTo("SUP-001")
    assertThat(found.get().recipientType).isEqualTo(InvoiceNetstone.RecipientType.COMPANY_CODIG)
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-IB02")
    val invoice =
      InvoiceNetstone(
        "FB-DEF-01",
        InvoiceNetstone.RecipientType.PRODUCER,
        client,
        LocalDate.of(2026, 1, 1),
      )

    assertThat(invoice.status).isEqualTo(InvoiceNetstone.InvoiceNetstoneStatus.RECEIVED)
    assertThat(invoice.origin).isEqualTo(InvoiceNetstone.Origin.ORDER_LINKED)
    assertThat(invoice.totalExclTax).isEqualByComparingTo("0")
    assertThat(invoice.totalVat).isEqualByComparingTo("0")
    assertThat(invoice.totalInclTax).isEqualByComparingTo("0")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-IB03")
    val invoice =
      InvoiceNetstone(
        "FB-DEL-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoiceNetstoneRepository.save(invoice)

    assertThat(invoiceNetstoneRepository.findByDeletedAtIsNull()).anyMatch {
      it.internalInvoiceNumber == "FB-DEL-01"
    }

    invoice.softDelete()
    invoiceNetstoneRepository.saveAndFlush(invoice)

    assertThat(invoiceNetstoneRepository.findByDeletedAtIsNull()).noneMatch {
      it.internalInvoiceNumber == "FB-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-IB04")
    val invoice =
      InvoiceNetstone(
        "FB-REST-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )

    invoice.softDelete()
    assertThat(invoice.isDeleted()).isTrue

    invoice.restore()
    assertThat(invoice.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-IB05")
    val invoice =
      InvoiceNetstone(
        "FB-TS-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoiceNetstoneRepository.save(invoice)

    assertThat(invoice.createdAt).isNotNull
    assertThat(invoice.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-IB06")
    for (status in InvoiceNetstone.InvoiceNetstoneStatus.entries) {
      val invoice =
        InvoiceNetstone(
          "FB-ST-${status.ordinal}",
          InvoiceNetstone.RecipientType.COMPANY_CODIG,
          client,
          LocalDate.of(2026, 3, 1),
        )
      invoice.status = status
      invoiceNetstoneRepository.save(invoice)

      val found = invoiceNetstoneRepository.findById(invoice.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun linked_to_orderNetstone() {
    val client = createClient("CLI-IB07")
    val orderCodig =
      orderCodigRepository.save(OrderCodig("CA-IB-01", client, LocalDate.of(2026, 3, 1)))
    val orderNetstone = orderNetstoneRepository.save(OrderNetstone("CB-IB-01", orderCodig))

    val invoice =
      InvoiceNetstone(
        "FB-LINK-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoice.orderNetstone = orderNetstone
    invoiceNetstoneRepository.save(invoice)

    val found = invoiceNetstoneRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.orderNetstone!!.id).isEqualTo(orderNetstone.id)
  }

  @Test
  fun dispute_fields() {
    val client = createClient("CLI-IB08")
    val invoice =
      InvoiceNetstone(
        "FB-DISP-01",
        InvoiceNetstone.RecipientType.PRODUCER,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoice.status = InvoiceNetstone.InvoiceNetstoneStatus.DISPUTED
    invoice.disputeReason = "Amount mismatch"
    invoice.amountDiscrepancy = BigDecimal("150.50")
    invoiceNetstoneRepository.save(invoice)

    val found = invoiceNetstoneRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.disputeReason).isEqualTo("Amount mismatch")
    assertThat(found.amountDiscrepancy).isEqualByComparingTo("150.50")
  }

  @Test
  fun payment_and_verification_fields() {
    val client = createClient("CLI-IB09")
    val invoice =
      InvoiceNetstone(
        "FB-PAY-01",
        InvoiceNetstone.RecipientType.COMPANY_CODIG,
        client,
        LocalDate.of(2026, 3, 1),
      )
    invoice.dueDate = LocalDate.of(2026, 4, 1)
    invoice.verificationDate = LocalDate.of(2026, 3, 20)
    invoice.paymentDate = LocalDate.of(2026, 3, 25)
    invoice.pdfPath = "/invoices/FB-PAY-01.pdf"
    invoice.notes = "Verified and paid"
    invoiceNetstoneRepository.save(invoice)

    val found = invoiceNetstoneRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.dueDate).isEqualTo(LocalDate.of(2026, 4, 1))
    assertThat(found.verificationDate).isEqualTo(LocalDate.of(2026, 3, 20))
    assertThat(found.paymentDate).isEqualTo(LocalDate.of(2026, 3, 25))
    assertThat(found.pdfPath).isEqualTo("/invoices/FB-PAY-01.pdf")
    assertThat(found.notes).isEqualTo("Verified and paid")
  }
}
