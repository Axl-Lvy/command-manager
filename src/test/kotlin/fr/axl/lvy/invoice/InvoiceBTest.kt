package fr.axl.lvy.invoice

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderARepository
import fr.axl.lvy.order.OrderB
import fr.axl.lvy.order.OrderBRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class InvoiceBTest {

  @Autowired lateinit var invoiceBRepository: InvoiceBRepository
  @Autowired lateinit var clientRepository: ClientRepository
  @Autowired lateinit var orderARepository: OrderARepository
  @Autowired lateinit var orderBRepository: OrderBRepository

  private fun createClient(code: String): Client =
    clientRepository.save(Client(code, "Client $code"))

  @Test
  fun save_and_retrieve_invoice() {
    val client = createClient("CLI-IB01")
    val invoice =
      InvoiceB("FB-2026-0001", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))
    invoice.supplierInvoiceNumber = "SUP-001"
    invoiceBRepository.save(invoice)

    val found = invoiceBRepository.findById(invoice.id!!)
    assertThat(found).isPresent
    assertThat(found.get().internalInvoiceNumber).isEqualTo("FB-2026-0001")
    assertThat(found.get().supplierInvoiceNumber).isEqualTo("SUP-001")
    assertThat(found.get().recipientType).isEqualTo(InvoiceB.RecipientType.COMPANY_A)
  }

  @Test
  fun defaults_are_correct() {
    val client = createClient("CLI-IB02")
    val invoice =
      InvoiceB("FB-DEF-01", InvoiceB.RecipientType.PRODUCER, client, LocalDate.of(2026, 1, 1))

    assertThat(invoice.status).isEqualTo(InvoiceB.InvoiceBStatus.RECEIVED)
    assertThat(invoice.origin).isEqualTo(InvoiceB.Origin.ORDER_LINKED)
    assertThat(invoice.totalExclTax).isEqualByComparingTo("0")
    assertThat(invoice.totalVat).isEqualByComparingTo("0")
    assertThat(invoice.totalInclTax).isEqualByComparingTo("0")
  }

  @Test
  fun soft_delete_excludes_from_findAll() {
    val client = createClient("CLI-IB03")
    val invoice =
      InvoiceB("FB-DEL-01", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))
    invoiceBRepository.save(invoice)

    assertThat(invoiceBRepository.findByDeletedAtIsNull()).anyMatch {
      it.internalInvoiceNumber == "FB-DEL-01"
    }

    invoice.softDelete()
    invoiceBRepository.saveAndFlush(invoice)

    assertThat(invoiceBRepository.findByDeletedAtIsNull()).noneMatch {
      it.internalInvoiceNumber == "FB-DEL-01"
    }
  }

  @Test
  fun restore_after_soft_delete() {
    val client = createClient("CLI-IB04")
    val invoice =
      InvoiceB("FB-REST-01", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))

    invoice.softDelete()
    assertThat(invoice.isDeleted()).isTrue

    invoice.restore()
    assertThat(invoice.isDeleted()).isFalse
  }

  @Test
  fun timestamps_set_on_persist() {
    val client = createClient("CLI-IB05")
    val invoice =
      InvoiceB("FB-TS-01", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))
    invoiceBRepository.save(invoice)

    assertThat(invoice.createdAt).isNotNull
    assertThat(invoice.updatedAt).isNotNull
  }

  @Test
  fun all_statuses_can_be_persisted() {
    val client = createClient("CLI-IB06")
    for (status in InvoiceB.InvoiceBStatus.entries) {
      val invoice =
        InvoiceB(
          "FB-ST-${status.ordinal}",
          InvoiceB.RecipientType.COMPANY_A,
          client,
          LocalDate.of(2026, 3, 1),
        )
      invoice.status = status
      invoiceBRepository.save(invoice)

      val found = invoiceBRepository.findById(invoice.id!!).orElseThrow()
      assertThat(found.status).isEqualTo(status)
    }
  }

  @Test
  fun linked_to_orderB() {
    val client = createClient("CLI-IB07")
    val orderA = orderARepository.save(OrderA("CA-IB-01", client, LocalDate.of(2026, 3, 1)))
    val orderB = orderBRepository.save(OrderB("CB-IB-01", orderA))

    val invoice =
      InvoiceB("FB-LINK-01", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))
    invoice.orderB = orderB
    invoiceBRepository.save(invoice)

    val found = invoiceBRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.orderB!!.id).isEqualTo(orderB.id)
  }

  @Test
  fun dispute_fields() {
    val client = createClient("CLI-IB08")
    val invoice =
      InvoiceB("FB-DISP-01", InvoiceB.RecipientType.PRODUCER, client, LocalDate.of(2026, 3, 1))
    invoice.status = InvoiceB.InvoiceBStatus.DISPUTED
    invoice.disputeReason = "Amount mismatch"
    invoice.amountDiscrepancy = BigDecimal("150.50")
    invoiceBRepository.save(invoice)

    val found = invoiceBRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.disputeReason).isEqualTo("Amount mismatch")
    assertThat(found.amountDiscrepancy).isEqualByComparingTo("150.50")
  }

  @Test
  fun payment_and_verification_fields() {
    val client = createClient("CLI-IB09")
    val invoice =
      InvoiceB("FB-PAY-01", InvoiceB.RecipientType.COMPANY_A, client, LocalDate.of(2026, 3, 1))
    invoice.dueDate = LocalDate.of(2026, 4, 1)
    invoice.verificationDate = LocalDate.of(2026, 3, 20)
    invoice.paymentDate = LocalDate.of(2026, 3, 25)
    invoice.pdfPath = "/invoices/FB-PAY-01.pdf"
    invoice.notes = "Verified and paid"
    invoiceBRepository.save(invoice)

    val found = invoiceBRepository.findById(invoice.id!!).orElseThrow()
    assertThat(found.dueDate).isEqualTo(LocalDate.of(2026, 4, 1))
    assertThat(found.verificationDate).isEqualTo(LocalDate.of(2026, 3, 20))
    assertThat(found.paymentDate).isEqualTo(LocalDate.of(2026, 3, 25))
    assertThat(found.pdfPath).isEqualTo("/invoices/FB-PAY-01.pdf")
    assertThat(found.notes).isEqualTo("Verified and paid")
  }
}
