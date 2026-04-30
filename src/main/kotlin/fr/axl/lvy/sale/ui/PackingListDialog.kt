package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.server.StreamResource
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.pdf.PdfService
import fr.axl.lvy.sale.SalesCodig
import java.time.LocalDate

/**
 * Non-persistent dialog that collects Packing List fields and generates the corresponding PDF on
 * demand. Fields are prefilled from the related sale and its first product line when available.
 */
internal class PackingListDialog(
  private val pdfService: PdfService,
  private val sale: SalesCodig,
  saleLines: List<DocumentLine>,
) : Dialog() {

  private val productCode = TextField("Product")
  private val productDescription = TextField("Product description")
  private val poNumber = TextField("Your PO number")
  private val pcCode = TextField("PC")
  private val packingListNumber = TextField("Packing List Nr")
  private val invoiceNumber = TextField("Invoice Nr")
  private val batchNumber = TextField("Batch number")
  private val quantity = TextField("Quantity")
  private val isoTankNumber = TextField("Iso tank Nr")
  private val origin = TextField("Origin")
  private val date = DatePicker("Date")
  private val packageDescription = TextField("Package")
  private val grossWeight = TextField("GW")
  private val netWeight = TextField("NW")
  private val casNumber = TextField("CAS #")
  private val ecNumber = TextField("EC #")
  private val hazardNote = TextArea("Hazard note")

  init {
    headerTitle = "Packing List"
    width = "720px"
    height = "90%"

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(productCode, productDescription)
    form.add(poNumber, pcCode)
    form.add(packingListNumber, invoiceNumber)
    form.add(batchNumber, quantity)
    form.add(isoTankNumber, origin)
    form.add(date, packageDescription)
    form.add(grossWeight, netWeight)
    form.add(casNumber, ecNumber)
    form.add(hazardNote, 2)
    add(form)

    prefill(saleLines)

    val downloadBtn = Button("Télécharger PDF")
    downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val downloadLink =
      Anchor(buildPdfResource(), "").apply {
        element.setAttribute("download", true)
        add(downloadBtn)
      }
    val cancelBtn = Button("Fermer") { close() }
    footer.add(HorizontalLayout(downloadLink, cancelBtn))
  }

  private fun buildPdfResource(): StreamResource {
    val baseName =
      packingListNumber.value?.takeIf { it.isNotBlank() }?.replace("/", "_")?.replace(" ", "_")
        ?: "packing-list-${sale.saleNumber.replace("/", "_")}"
    return StreamResource("$baseName.pdf") {
        pdfService.generatePackingListPdf(currentInput()).inputStream()
      }
      .apply { cacheTime = 0 }
  }

  private fun currentInput() =
    PdfService.PackingListInput(
      productCode = productCode.value.orEmpty().trim(),
      productDescription = productDescription.value.takeIf { !it.isNullOrBlank() }?.trim(),
      poNumber = poNumber.value.orEmpty().trim(),
      pcCode = pcCode.value.takeIf { !it.isNullOrBlank() }?.trim(),
      packingListNumber = packingListNumber.value.orEmpty().trim(),
      invoiceNumber = invoiceNumber.value.orEmpty().trim(),
      batchNumber = batchNumber.value.orEmpty().trim(),
      quantity = quantity.value.orEmpty().trim(),
      isoTankNumber = isoTankNumber.value.orEmpty().trim(),
      origin = origin.value.orEmpty().trim(),
      date = date.value,
      packageDescription = packageDescription.value.orEmpty().trim(),
      grossWeight = grossWeight.value.orEmpty().trim(),
      netWeight = netWeight.value.orEmpty().trim(),
      casNumber = casNumber.value.takeIf { !it.isNullOrBlank() }?.trim(),
      ecNumber = ecNumber.value.takeIf { !it.isNullOrBlank() }?.trim(),
      hazardNote = hazardNote.value.takeIf { !it.isNullOrBlank() }?.trim(),
    )

  private fun prefill(saleLines: List<DocumentLine>) {
    val firstLine = saleLines.firstOrNull()
    val product = firstLine?.product
    productCode.value = product?.reference ?: firstLine?.designation.orEmpty()
    productDescription.value = product?.label ?: product?.shortDescription ?: ""
    poNumber.value = sale.clientReference.orEmpty()
    pcCode.value = product?.findClientProductCode(sale.client).orEmpty()
    invoiceNumber.value = ""
    packingListNumber.value = ""
    batchNumber.value = ""
    val unit = firstLine?.unit ?: product?.unit ?: ""
    quantity.value =
      firstLine
        ?.quantity
        ?.let { qty -> if (unit.isNotBlank()) "$qty $unit" else qty.toPlainString() }
        .orEmpty()
    isoTankNumber.value = ""
    origin.value = product?.madeIn ?: firstLine?.madeIn.orEmpty()
    date.value = LocalDate.now()
    packageDescription.value = ""
    grossWeight.value = ""
    netWeight.value = ""
    casNumber.value = product?.casNumber.orEmpty()
    ecNumber.value = product?.ecNumber.orEmpty()
    hazardNote.value = ""
  }
}
