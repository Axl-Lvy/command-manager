# Code Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all issues identified in the feat/order code review: auto-create number sequences, fix copyFieldsFrom, deduplicate recalculateTotals, centralize prefixes, fix OrderA/OrderB relationship, move business logic from UI to services, rename Sales UI classes, and extract shared test helpers.

**Architecture:** Bottom-up approach -- fix foundational concerns first (entities, services), then update the UI layer, and finally clean up tests. Each task is independently testable.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Vaadin, H2 (test), JUnit 5, AssertJ

---

### Task 1: NumberSequence auto-creation + remove test seeding

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/base/NumberSequenceRepository.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/base/NumberSequenceService.kt`
- Modify: `src/test/kotlin/fr/axl/lvy/base/NumberSequenceServiceTest.kt`
- Delete: `src/test/resources/data.sql`
- Modify: `src/test/resources/application.properties` (remove `defer-datasource-initialization`)

- [ ] **Step 1: Modify NumberSequenceRepository to return nullable**

Change `findForUpdate` to return `NumberSequence?`:

```kotlin
// NumberSequenceRepository.kt
interface NumberSequenceRepository : JpaRepository<NumberSequence, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT n FROM NumberSequence n WHERE n.entityType = :type")
  fun findForUpdate(@Param("type") type: String): NumberSequence?
}
```

- [ ] **Step 2: Modify NumberSequenceService to auto-create missing rows**

```kotlin
// NumberSequenceService.kt
@Service
class NumberSequenceService(private val repository: NumberSequenceRepository) {

  @Transactional
  fun nextNumber(entityType: String, prefix: String, padding: Int): String {
    val seq = repository.findForUpdate(entityType)
      ?: repository.save(NumberSequence(entityType, 1))
    val current = seq.nextVal
    seq.nextVal++
    repository.save(seq)
    return prefix + current.toString().padStart(padding, '0')
  }

  companion object {
    const val CLIENT = "CLIENT"
    const val ORDER_A = "ORDER_A"
    const val ORDER_B = "ORDER_B"
    const val SALES_A = "SALES_A"
    const val SALES_B = "SALES_B"
  }
}
```

- [ ] **Step 3: Delete data.sql and remove defer-datasource-initialization**

Delete `src/test/resources/data.sql` entirely.

In `src/test/resources/application.properties`, remove the `spring.jpa.defer-datasource-initialization=true` line:

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.profiles.active=
```

- [ ] **Step 4: Add test for auto-creation of missing sequence**

Add to `NumberSequenceServiceTest.kt`:

```kotlin
@Test
fun nextNumber_auto_creates_sequence_when_missing() {
  val first = numberSequenceService.nextNumber("NEW_TYPE", "NEW_", 4)
  val second = numberSequenceService.nextNumber("NEW_TYPE", "NEW_", 4)

  assertThat(first).isEqualTo("NEW_0001")
  assertThat(second).isEqualTo("NEW_0002")
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: All tests pass, including the new auto-creation test. The existing tests still work because `nextNumber` auto-creates the rows that `data.sql` used to seed.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "fix: auto-create number sequences, remove test data.sql seeding"
```

---

### Task 2: Add designation to DocumentLine.copyFieldsFrom

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/documentline/DocumentLine.kt`
- Modify: `src/test/kotlin/fr/axl/lvy/documentline/DocumentLineTest.kt`

- [ ] **Step 1: Add designation to copyFieldsFrom**

In `DocumentLine.kt`, add `designation = source.designation` to `copyFieldsFrom`:

```kotlin
fun copyFieldsFrom(
  source: DocumentLine,
  overrideVatRate: BigDecimal? = null,
  overrideUnitPrice: BigDecimal? = null,
) {
  designation = source.designation
  product = source.product
  description = source.description
  hsCode = source.hsCode
  madeIn = source.madeIn
  clientProductCode = source.clientProductCode
  quantity = source.quantity
  unit = source.unit
  unitPriceExclTax = overrideUnitPrice ?: source.unitPriceExclTax
  discountPercent = source.discountPercent
  vatRate = overrideVatRate ?: source.vatRate
  recalculate()
}
```

- [ ] **Step 2: Update the copyFieldsFrom test to verify designation is copied**

In `DocumentLineTest.kt`, in the `copyFieldsFrom_copies_all_fields` test, add an assertion after `target.copyFieldsFrom(source)`:

```kotlin
assertThat(target.designation).isEqualTo("Source")
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/fr/axl/lvy/documentline/DocumentLine.kt src/test/kotlin/fr/axl/lvy/documentline/DocumentLineTest.kt
git commit -m "fix: copy designation in DocumentLine.copyFieldsFrom"
```

---

### Task 3: Deduplicate recalculateTotals

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/documentline/DocumentLine.kt` (add computeTotals companion)
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderA.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderB.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesA.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesB.kt`

- [ ] **Step 1: Add Totals data class and computeTotals to DocumentLine companion**

In `DocumentLine.kt`, add inside the `companion object`:

```kotlin
data class Totals(val exclTax: BigDecimal, val vat: BigDecimal, val inclTax: BigDecimal)

fun computeTotals(lines: List<DocumentLine>): Totals {
  val exclTax = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.lineTotalExclTax) }
  val vat = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.vatAmount) }
  return Totals(exclTax, vat, exclTax.add(vat))
}
```

- [ ] **Step 2: Update OrderA.recalculateTotals**

```kotlin
fun recalculateTotals(lines: List<DocumentLine>) {
  val totals = DocumentLine.computeTotals(lines)
  totalExclTax = totals.exclTax
  totalVat = totals.vat
  totalInclTax = totals.inclTax
  marginExclTax = BigDecimal.ZERO
}
```

- [ ] **Step 3: Update OrderB.recalculateTotals**

```kotlin
fun recalculateTotals(lines: List<DocumentLine>) {
  val totals = DocumentLine.computeTotals(lines)
  totalExclTax = totals.exclTax
  totalVat = totals.vat
  totalInclTax = totals.inclTax
}
```

- [ ] **Step 4: Update SalesA.recalculateTotals**

Same as OrderB (3-line delegation to `DocumentLine.computeTotals`).

- [ ] **Step 5: Update SalesB.recalculateTotals**

Same as OrderB (3-line delegation to `DocumentLine.computeTotals`).

- [ ] **Step 6: Run tests**

Run: `./gradlew test`
Expected: All recalculateTotals tests pass unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/fr/axl/lvy/documentline/DocumentLine.kt src/main/kotlin/fr/axl/lvy/order/OrderA.kt src/main/kotlin/fr/axl/lvy/order/OrderB.kt src/main/kotlin/fr/axl/lvy/sale/SalesA.kt src/main/kotlin/fr/axl/lvy/sale/SalesB.kt
git commit -m "refactor: deduplicate recalculateTotals via DocumentLine.computeTotals"
```

---

### Task 4: Centralize number prefixes

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/base/NumberSequenceService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/client/ClientService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderAService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderBService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesAService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesBService.kt`

- [ ] **Step 1: Add SequenceConfig and single-param nextNumber to NumberSequenceService**

```kotlin
@Service
class NumberSequenceService(private val repository: NumberSequenceRepository) {

  @Transactional
  fun nextNumber(entityType: String): String {
    val config = CONFIGS[entityType]
      ?: throw IllegalArgumentException("Unknown entity type: $entityType")
    return nextNumber(entityType, config.prefix, config.padding)
  }

  @Transactional
  fun nextNumber(entityType: String, prefix: String, padding: Int): String {
    val seq = repository.findForUpdate(entityType)
      ?: repository.save(NumberSequence(entityType, 1))
    val current = seq.nextVal
    seq.nextVal++
    repository.save(seq)
    return prefix + current.toString().padStart(padding, '0')
  }

  data class SequenceConfig(val prefix: String, val padding: Int)

  companion object {
    const val CLIENT = "CLIENT"
    const val ORDER_A = "ORDER_A"
    const val ORDER_B = "ORDER_B"
    const val SALES_A = "SALES_A"
    const val SALES_B = "SALES_B"

    val CONFIGS = mapOf(
      CLIENT to SequenceConfig("C", 6),
      ORDER_A to SequenceConfig("CoD_PO_", 3),
      ORDER_B to SequenceConfig("NST_PO_", 3),
      SALES_A to SequenceConfig("CoD_SO_", 3),
      SALES_B to SequenceConfig("NST_SO_", 3),
    )
  }
}
```

- [ ] **Step 2: Update ClientService**

Replace:
```kotlin
private fun generateNextClientCode(): String =
  numberSequenceService.nextNumber(NumberSequenceService.CLIENT, "C", 6)
```
With:
```kotlin
private fun generateNextClientCode(): String =
  numberSequenceService.nextNumber(NumberSequenceService.CLIENT)
```

- [ ] **Step 3: Update OrderAService**

Replace:
```kotlin
private fun generateNextOrderNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.ORDER_A, "CoD_PO_", 3)
```
With:
```kotlin
private fun generateNextOrderNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.ORDER_A)
```

- [ ] **Step 4: Update OrderBService**

Replace:
```kotlin
private fun generateNextOrderNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.ORDER_B, "NST_PO_", 3)
```
With:
```kotlin
private fun generateNextOrderNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.ORDER_B)
```

- [ ] **Step 5: Update SalesAService**

Replace:
```kotlin
private fun generateNextSaleNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.SALES_A, "CoD_SO_", 3)
```
With:
```kotlin
private fun generateNextSaleNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.SALES_A)
```

- [ ] **Step 6: Update SalesBService**

Replace:
```kotlin
private fun generateNextSaleNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.SALES_B, "NST_SO_", 3)
```
With:
```kotlin
private fun generateNextSaleNumber(): String =
  numberSequenceService.nextNumber(NumberSequenceService.SALES_B)
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/fr/axl/lvy/base/NumberSequenceService.kt src/main/kotlin/fr/axl/lvy/client/ClientService.kt src/main/kotlin/fr/axl/lvy/order/OrderAService.kt src/main/kotlin/fr/axl/lvy/order/OrderBService.kt src/main/kotlin/fr/axl/lvy/sale/SalesAService.kt src/main/kotlin/fr/axl/lvy/sale/SalesBService.kt
git commit -m "refactor: centralize number sequence prefixes in NumberSequenceService"
```

---

### Task 5: Fix OrderA/OrderB bidirectional relationship

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderA.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderB.kt`

Currently `OrderA` has `@OneToOne @JoinColumn("order_b_id")` and `OrderB` has `@ManyToOne @JoinColumn("order_a_id")`, creating two independent FK columns. Fix: single FK on `OrderB` side.

- [ ] **Step 1: Change OrderB.orderA from @ManyToOne to @OneToOne**

In `OrderB.kt`, change:
```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "order_a_id", nullable = false)
var orderA: OrderA,
```
To:
```kotlin
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "order_a_id", nullable = false)
var orderA: OrderA,
```

- [ ] **Step 2: Change OrderA.orderB to use mappedBy**

In `OrderA.kt`, change:
```kotlin
@OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_b_id") var orderB: OrderB? = null
```
To:
```kotlin
@OneToOne(mappedBy = "orderA", fetch = FetchType.LAZY) var orderB: OrderB? = null
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test`
Expected: All tests pass. The `handleMto` test and SalesB sync tests should still work because `OrderB(number, orderA)` correctly sets the owning side FK, and `order.orderB = orderB` just updates the in-memory inverse reference.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/fr/axl/lvy/order/OrderA.kt src/main/kotlin/fr/axl/lvy/order/OrderB.kt
git commit -m "fix: use single FK for OrderA/OrderB relationship (mappedBy on OrderA)"
```

---

### Task 6: Move business logic from UI to services

**Files:**
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesAService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/sale/SalesBService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderAService.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/OrderBService.kt`

Add `findLines` and `saveWithLines` methods to all four services, encapsulating the line CRUD + total recalculation + sync logic that currently lives in form dialogs.

- [ ] **Step 1: Add findLines and saveWithLines to SalesAService**

```kotlin
@Transactional(readOnly = true)
fun findLines(saleId: Long): List<DocumentLine> =
  documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.SALES_A, saleId
  )

@Transactional
fun saveWithLines(sale: SalesA, lines: List<DocumentLine>): SalesA {
  val saved = save(sale)

  val existingLines = documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.SALES_A, saved.id!!
  )
  documentLineRepository.deleteAll(existingLines)

  lines.forEachIndexed { i, line ->
    line.documentType = DocumentLine.DocumentType.SALES_A
    line.documentId = saved.id!!
    line.position = i
    line.vatRate = saved.vatRate
    line.recalculate()
    documentLineRepository.save(line)
  }

  saved.recalculateTotals(lines)
  return syncGeneratedOrder(saved, lines)
}
```

- [ ] **Step 2: Add findLines and saveWithLines to SalesBService**

```kotlin
@Transactional(readOnly = true)
fun findLines(saleId: Long): List<DocumentLine> =
  documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.SALES_B, saleId
  )

@Transactional
fun saveWithLines(sale: SalesB, lines: List<DocumentLine>): SalesB {
  val saved = save(sale)

  val existingLines = documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.SALES_B, saved.id!!
  )
  documentLineRepository.deleteAll(existingLines)

  lines.forEachIndexed { i, line ->
    line.documentType = DocumentLine.DocumentType.SALES_B
    line.documentId = saved.id!!
    line.position = i
    line.recalculate()
    documentLineRepository.save(line)
  }

  saved.recalculateTotals(lines)
  return syncGeneratedOrder(saved, lines)
}
```

- [ ] **Step 3: Add DocumentLineRepository to OrderBService constructor and add findLines/saveWithLines**

OrderBService currently does not inject `DocumentLineRepository`. Add it to the constructor:

```kotlin
@Service
class OrderBService(
  private val orderBRepository: OrderBRepository,
  private val documentLineRepository: DocumentLineRepository,
  private val numberSequenceService: NumberSequenceService,
) {
```

Add the import: `import fr.axl.lvy.documentline.DocumentLineRepository`

Then add:

```kotlin
@Transactional(readOnly = true)
fun findLines(orderId: Long): List<DocumentLine> =
  documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.ORDER_B, orderId
  )

@Transactional
fun saveWithLines(order: OrderB, lines: List<DocumentLine>): OrderB {
  val saved = save(order)

  val existingLines = documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.ORDER_B, saved.id!!
  )
  documentLineRepository.deleteAll(existingLines)

  lines.forEachIndexed { i, line ->
    line.documentType = DocumentLine.DocumentType.ORDER_B
    line.documentId = saved.id!!
    line.position = i
    line.recalculate()
    documentLineRepository.save(line)
  }

  saved.recalculateTotals(lines)
  return orderBRepository.save(saved)
}
```

- [ ] **Step 4: Add findLines and saveWithLines to OrderAService**

OrderAService already injects `documentLineRepository`. Add:

```kotlin
@Transactional(readOnly = true)
fun findLines(orderId: Long): List<DocumentLine> =
  documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.ORDER_A, orderId
  )

@Transactional
fun saveWithLines(order: OrderA, lines: List<DocumentLine>): OrderA {
  val saved = save(order)

  val existingLines = documentLineRepository.findByDocumentTypeAndDocumentIdOrderByPosition(
    DocumentLine.DocumentType.ORDER_A, saved.id!!
  )
  documentLineRepository.deleteAll(existingLines)

  lines.forEachIndexed { i, line ->
    line.documentType = DocumentLine.DocumentType.ORDER_A
    line.documentId = saved.id!!
    line.position = i
    line.vatRate = saved.vatRate
    line.recalculate()
    documentLineRepository.save(line)
  }

  saved.recalculateTotals(lines)
  return orderARepository.save(saved)
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: All tests pass (new methods are additive).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/fr/axl/lvy/sale/SalesAService.kt src/main/kotlin/fr/axl/lvy/sale/SalesBService.kt src/main/kotlin/fr/axl/lvy/order/OrderAService.kt src/main/kotlin/fr/axl/lvy/order/OrderBService.kt
git commit -m "refactor: add saveWithLines/findLines to services for transactional line management"
```

---

### Task 7: Rename Sales UI classes + use service methods

**Files:**
- Rename+Move: `src/main/kotlin/fr/axl/lvy/order/ui/OrderAFormDialog.kt` -> `src/main/kotlin/fr/axl/lvy/sale/ui/SalesAFormDialog.kt`
- Rename+Move: `src/main/kotlin/fr/axl/lvy/order/ui/OrderAListView.kt` -> `src/main/kotlin/fr/axl/lvy/sale/ui/SalesAListView.kt`
- Rename+Move: `src/main/kotlin/fr/axl/lvy/order/ui/OrderBFormDialog.kt` -> `src/main/kotlin/fr/axl/lvy/sale/ui/SalesBFormDialog.kt`
- Rename+Move: `src/main/kotlin/fr/axl/lvy/order/ui/OrderBListView.kt` -> `src/main/kotlin/fr/axl/lvy/sale/ui/SalesBListView.kt`
- Modify: `src/main/kotlin/fr/axl/lvy/order/ui/CommandAFormDialog.kt` (use saveWithLines, remove documentLineRepository)
- Modify: `src/main/kotlin/fr/axl/lvy/order/ui/CommandAListView.kt` (remove documentLineRepository)
- Modify: `src/main/kotlin/fr/axl/lvy/order/ui/CommandBFormDialog.kt` (use saveWithLines, remove documentLineRepository)
- Modify: `src/main/kotlin/fr/axl/lvy/order/ui/CommandBListView.kt` (remove documentLineRepository)

- [ ] **Step 1: Create sale/ui directory**

```bash
mkdir -p src/main/kotlin/fr/axl/lvy/sale/ui
```

- [ ] **Step 2: Create SalesAFormDialog**

Create `src/main/kotlin/fr/axl/lvy/sale/ui/SalesAFormDialog.kt` -- renamed from `OrderAFormDialog`, package changed to `fr.axl.lvy.sale.ui`, `documentLineRepository` removed, `save()` uses `salesAService.saveWithLines()`, `populateForm()` uses `salesAService.findLines()`:

```kotlin
package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
import java.math.BigDecimal
import java.time.LocalDate

internal class SalesAFormDialog(
  private val salesAService: SalesAService,
  clientService: ClientService,
  productService: ProductService,
  private val order: SalesA?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N\u00b0 Vente")
  private val clientCombo = ComboBox<Client>("Client")
  private val orderDate = DatePicker("Date vente")
  private val status = ComboBox<SalesA.SalesAStatus>("Statut")
  private val expectedDeliveryDate = DatePicker("Livraison pr\u00e9vue")
  private val clientReference = TextField("R\u00e9f. client")
  private val subject = TextField("Objet")
  private val sellingPrice = BigDecimalField("Prix vente HT")
  private val currency = ComboBox<String>("Devise")
  private val vatRate = BigDecimalField("TVA (%)")
  private val incoterms = TextField("Incoterms")
  private val billingAddress = TextArea("Adresse facturation")
  private val shippingAddress = TextArea("Adresse livraison")
  private val notes = TextArea("Notes")
  private val conditions = TextArea("Conditions")
  private val lineEditor: DocumentLineEditor

  init {
    headerTitle = if (order == null) "Nouvelle vente A" else "Modifier vente A"
    width = "900px"
    height = "90%"

    clientCombo.isRequired = true
    orderDate.isRequired = true
    orderNumber.isReadOnly = true
    sellingPrice.isReadOnly = true
    currency.setItems("EUR", "$")
    status.setItems(*SalesA.SalesAStatus.entries.toTypedArray())
    status.setItemLabelGenerator {
      when (it) {
        SalesA.SalesAStatus.DRAFT -> "Brouillon"
        SalesA.SalesAStatus.VALIDATED -> "Validee"
        SalesA.SalesAStatus.CANCELLED -> "Annulee"
      }
    }

    clientCombo.setItems(clientService.findAll().filter { it.isClient() })
    clientCombo.setItemLabelGenerator { "${it.clientCode} - ${it.name}" }
    clientCombo.addValueChangeListener { event ->
      val client = event.value ?: return@addValueChangeListener
      billingAddress.value = client.billingAddress ?: ""
      shippingAddress.value = client.shippingAddress ?: ""
    }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 3))
    form.add(orderNumber, clientCombo, orderDate)
    form.add(status, expectedDeliveryDate, clientReference)
    form.add(subject, sellingPrice, currency)
    form.add(vatRate, incoterms)
    form.add(billingAddress, 3)
    form.add(shippingAddress, 3)
    form.add(notes, 3)
    form.add(conditions, 3)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.SALES_A) { clientCombo.value }

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
      orderDate.value = LocalDate.now()
      status.value = SalesA.SalesAStatus.DRAFT
      currency.value = "EUR"
      vatRate.value = BigDecimal("20.00")
      sellingPrice.value = BigDecimal.ZERO
    }
  }

  private fun populateForm(o: SalesA) {
    orderNumber.value = o.saleNumber
    clientCombo.value = o.client
    orderDate.value = o.saleDate
    status.value = o.status
    expectedDeliveryDate.value = o.expectedDeliveryDate
    clientReference.value = o.clientReference ?: ""
    subject.value = o.subject ?: ""
    sellingPrice.value = o.totalExclTax
    currency.value = o.currency
    vatRate.value = o.vatRate
    incoterms.value = o.incoterms ?: ""
    billingAddress.value = o.billingAddress ?: ""
    shippingAddress.value = o.shippingAddress ?: ""
    notes.value = o.notes ?: ""
    conditions.value = o.conditions ?: ""

    lineEditor.setLines(salesAService.findLines(o.id!!))
  }

  private fun save() {
    if (clientCombo.isEmpty || orderDate.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: SalesA("", clientCombo.value, orderDate.value)
    if (order != null) {
      o.client = clientCombo.value
      o.saleDate = orderDate.value
    }
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.status = status.value ?: SalesA.SalesAStatus.DRAFT
    o.clientReference = if (clientReference.value.isBlank()) null else clientReference.value
    o.subject = if (subject.value.isBlank()) null else subject.value
    o.currency = currency.value ?: "EUR"
    o.vatRate = vatRate.value ?: BigDecimal.ZERO
    o.incoterms = if (incoterms.value.isBlank()) null else incoterms.value
    o.billingAddress = if (billingAddress.value.isBlank()) null else billingAddress.value
    o.shippingAddress = if (shippingAddress.value.isBlank()) null else shippingAddress.value
    o.notes = if (notes.value.isBlank()) null else notes.value
    o.conditions = if (conditions.value.isBlank()) null else conditions.value

    val saved = salesAService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.saleNumber
    sellingPrice.value = saved.totalExclTax

    Notification.show("Vente A enregistr\u00e9e", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
```

- [ ] **Step 3: Create SalesAListView**

Create `src/main/kotlin/fr/axl/lvy/sale/ui/SalesAListView.kt`:

```kotlin
package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService

@Route("ventes-a")
@PageTitle("Ventes A")
@Menu(order = 3.0, icon = "vaadin:cart", title = "Ventes A")
internal class SalesAListView(
  private val salesAService: SalesAService,
  private val clientService: ClientService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesA>

  init {
    val addBtn = Button("Nouvelle vente") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesA::saleNumber).setHeader("N\u00b0 Vente").setAutoWidth(true)
    grid.addColumn { it.client.name }.setHeader("Client").setFlexGrow(1)
    grid.addColumn(SalesA::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesA::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesA::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente A")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes A", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesA?) {
    SalesAFormDialog(salesAService, clientService, productService, order, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(salesAService.findAll())
  }
}
```

- [ ] **Step 4: Create SalesBFormDialog**

Create `src/main/kotlin/fr/axl/lvy/sale/ui/SalesBFormDialog.kt` -- same pattern as SalesAFormDialog but for SalesB. Uses `salesBService.saveWithLines()` and `salesBService.findLines()`. No `documentLineRepository`:

```kotlin
package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.ui.DocumentLineEditor
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
import fr.axl.lvy.sale.SalesB
import fr.axl.lvy.sale.SalesBService

internal class SalesBFormDialog(
  private val salesBService: SalesBService,
  salesAService: SalesAService,
  productService: ProductService,
  private val order: SalesB?,
  private val onSave: Runnable,
) : Dialog() {

  private val orderNumber = TextField("N\u00b0 Vente B")
  private val orderACombo = ComboBox<SalesA>("Vente A li\u00e9e")
  private val orderDate = DatePicker("Date vente")
  private val expectedDeliveryDate = DatePicker("Livraison pr\u00e9vue")
  private val notes = TextArea("Notes")
  private val lineEditor: DocumentLineEditor

  init {
    setHeaderTitle(if (order == null) "Nouvelle vente B" else "Modifier vente B")
    setWidth("900px")
    setHeight("90%")

    orderACombo.isRequired = true
    orderNumber.isReadOnly = true

    orderACombo.setItems(salesAService.findAll())
    orderACombo.setItemLabelGenerator { "${it.saleNumber} - ${it.client.name}" }

    val form = FormLayout()
    form.setResponsiveSteps(FormLayout.ResponsiveStep("0", 2))
    form.add(orderNumber, orderACombo)
    form.add(orderDate, expectedDeliveryDate)
    form.add(notes, 2)

    lineEditor =
      DocumentLineEditor(productService, DocumentLine.DocumentType.SALES_B) {
        orderACombo.value?.client
      }

    val content = VerticalLayout(form, lineEditor)
    content.isPadding = false
    add(content)

    val saveBtn = Button("Enregistrer") { save() }
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
    val cancelBtn = Button("Annuler") { close() }
    footer.add(HorizontalLayout(saveBtn, cancelBtn))

    if (order != null) {
      populateForm(order)
    } else {
      orderNumber.value = "(auto)"
    }
  }

  private fun populateForm(o: SalesB) {
    orderNumber.value = o.saleNumber
    orderACombo.value = o.salesA
    orderDate.value = o.saleDate
    expectedDeliveryDate.value = o.expectedDeliveryDate
    notes.value = o.notes ?: ""

    lineEditor.setLines(salesBService.findLines(o.id!!))
  }

  private fun save() {
    if (orderACombo.isEmpty) {
      Notification.show(
          "Veuillez remplir les champs obligatoires",
          3000,
          Notification.Position.BOTTOM_END,
        )
        .addThemeVariants(NotificationVariant.LUMO_ERROR)
      return
    }

    val o = order ?: SalesB("", orderACombo.value)
    if (order != null) {
      o.salesA = orderACombo.value
    }
    o.saleDate = orderDate.value
    o.expectedDeliveryDate = expectedDeliveryDate.value
    o.notes = if (notes.value.isBlank()) null else notes.value

    val saved = salesBService.saveWithLines(o, lineEditor.getLines())
    orderNumber.value = saved.saleNumber

    Notification.show("Vente B enregistr\u00e9e", 3000, Notification.Position.BOTTOM_END)
      .addThemeVariants(NotificationVariant.LUMO_SUCCESS)
    onSave.run()
    close()
  }
}
```

- [ ] **Step 5: Create SalesBListView**

Create `src/main/kotlin/fr/axl/lvy/sale/ui/SalesBListView.kt`:

```kotlin
package fr.axl.lvy.sale.ui

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.Menu
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import fr.axl.lvy.base.ui.ViewToolbar
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesAService
import fr.axl.lvy.sale.SalesB
import fr.axl.lvy.sale.SalesBService

@Route("ventes-b")
@PageTitle("Ventes B")
@Menu(order = 4.0, icon = "vaadin:truck", title = "Ventes B")
internal class SalesBListView(
  private val salesBService: SalesBService,
  private val salesAService: SalesAService,
  private val productService: ProductService,
) : VerticalLayout() {

  private val grid: Grid<SalesB>

  init {
    val addBtn = Button("Nouvelle vente B") { openForm(null) }
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

    grid = Grid()
    grid.addColumn(SalesB::saleNumber).setHeader("N\u00b0 Vente B").setAutoWidth(true)
    grid.addColumn { it.salesA.saleNumber }.setHeader("Vente A li\u00e9e").setAutoWidth(true)
    grid.addColumn(SalesB::saleDate).setHeader("Date").setAutoWidth(true)
    grid.addColumn(SalesB::totalExclTax).setHeader("Total HT").setAutoWidth(true)
    grid.addColumn(SalesB::totalInclTax).setHeader("Total TTC").setAutoWidth(true)
    grid.addColumn { it.status.name }.setHeader("Statut").setAutoWidth(true)
    grid.setEmptyStateText("Aucune vente B")
    grid.setSizeFull()
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER)
    grid.addItemDoubleClickListener { openForm(it.item) }

    refreshGrid()

    setSizeFull()
    isPadding = false
    isSpacing = false
    style.setOverflow(Style.Overflow.HIDDEN)

    add(ViewToolbar("Ventes B", addBtn))
    add(grid)
  }

  private fun openForm(order: SalesB?) {
    SalesBFormDialog(salesBService, salesAService, productService, order, this::refreshGrid).open()
  }

  private fun refreshGrid() {
    grid.setItems(salesBService.findAll())
  }
}
```

- [ ] **Step 6: Delete old Order*FormDialog and Order*ListView files from order/ui**

Delete these 4 files:
- `src/main/kotlin/fr/axl/lvy/order/ui/OrderAFormDialog.kt`
- `src/main/kotlin/fr/axl/lvy/order/ui/OrderAListView.kt`
- `src/main/kotlin/fr/axl/lvy/order/ui/OrderBFormDialog.kt`
- `src/main/kotlin/fr/axl/lvy/order/ui/OrderBListView.kt`

- [ ] **Step 7: Update CommandAFormDialog to use service methods**

In `CommandAFormDialog.kt`:
1. Remove `documentLineRepository` from constructor
2. Replace `populateForm` line reads with `orderAService.findLines(o.id!!)`
3. Replace entire line management + save block in `save()` with `orderAService.saveWithLines(o, lineEditor.getLines())`
4. Remove `DocumentLineRepository` import

Updated constructor:
```kotlin
internal class CommandAFormDialog(
  private val orderAService: OrderAService,
  clientService: ClientService,
  productService: ProductService,
  private val order: OrderA?,
  private val onSave: Runnable,
) : Dialog() {
```

Updated `populateForm` line read:
```kotlin
lineEditor.setLines(orderAService.findLines(o.id!!))
```

Updated `save()` -- replace everything from `val saved = orderAService.save(o)` through `orderAService.save(saved)`:
```kotlin
val saved = orderAService.saveWithLines(o, lineEditor.getLines())
orderNumber.value = saved.orderNumber
totalExclTax.value = saved.totalExclTax
```

- [ ] **Step 8: Update CommandAListView to remove documentLineRepository**

In `CommandAListView.kt`:
1. Remove `documentLineRepository` from constructor
2. Update `openForm` to not pass `documentLineRepository`

Updated constructor:
```kotlin
internal class CommandAListView(
  private val orderAService: OrderAService,
  private val clientService: ClientService,
  private val productService: ProductService,
) : VerticalLayout() {
```

Updated `openForm`:
```kotlin
private fun openForm(order: OrderA?) {
  CommandAFormDialog(orderAService, clientService, productService, order, this::refreshGrid).open()
}
```

Remove the `import fr.axl.lvy.documentline.DocumentLineRepository` line.

- [ ] **Step 9: Update CommandBFormDialog to use service methods**

Same pattern as CommandAFormDialog:
1. Remove `documentLineRepository` from constructor
2. Replace line reads with `orderBService.findLines(o.id!!)`
3. Replace line management in `save()` with `orderBService.saveWithLines(o, lineEditor.getLines())`

Updated constructor:
```kotlin
internal class CommandBFormDialog(
  private val orderBService: OrderBService,
  orderAService: OrderAService,
  productService: ProductService,
  private val order: OrderB?,
  private val onSave: Runnable,
) : Dialog() {
```

Updated `populateForm` line read:
```kotlin
lineEditor.setLines(orderBService.findLines(o.id!!))
```

Updated `save()`:
```kotlin
val saved = orderBService.saveWithLines(o, lineEditor.getLines())
orderNumber.value = saved.orderNumber
```

- [ ] **Step 10: Update CommandBListView to remove documentLineRepository**

Same pattern:
```kotlin
internal class CommandBListView(
  private val orderBService: OrderBService,
  private val orderAService: OrderAService,
  private val productService: ProductService,
) : VerticalLayout() {
```

Updated `openForm`:
```kotlin
private fun openForm(order: OrderB?) {
  CommandBFormDialog(orderBService, orderAService, productService, order, this::refreshGrid).open()
}
```

Remove the `import fr.axl.lvy.documentline.DocumentLineRepository` line.

- [ ] **Step 11: Run tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "refactor: rename Sales UI classes, move to sale.ui package, use service saveWithLines"
```

---

### Task 8: Extract shared test helpers

**Files:**
- Create: `src/test/kotlin/fr/axl/lvy/TestDataFactory.kt`
- Modify: `src/test/kotlin/fr/axl/lvy/order/OrderAServiceTest.kt`
- Modify: `src/test/kotlin/fr/axl/lvy/sale/SalesAServiceTest.kt`
- Modify: `src/test/kotlin/fr/axl/lvy/sale/SalesBServiceTest.kt`

The `createClient` and `createDocumentLine` helper methods are duplicated across multiple test files. Extract them into a shared factory.

- [ ] **Step 1: Create TestDataFactory**

Create `src/test/kotlin/fr/axl/lvy/TestDataFactory.kt`:

```kotlin
package fr.axl.lvy

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.documentline.DocumentLineRepository
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductRepository
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class TestDataFactory(
  private val clientRepository: ClientRepository,
  private val documentLineRepository: DocumentLineRepository,
  private val productRepository: ProductRepository,
) {

  fun createClient(
    code: String,
    billingAddress: String? = null,
    shippingAddress: String? = null,
  ): Client {
    val client = Client(code, "Client $code")
    client.billingAddress = billingAddress
    client.shippingAddress = shippingAddress
    return clientRepository.save(client)
  }

  fun createDocumentLine(
    type: DocumentLine.DocumentType,
    documentId: Long,
    designation: String,
    quantity: BigDecimal = BigDecimal.ONE,
    unitPrice: BigDecimal = BigDecimal("100.00"),
    vatRate: BigDecimal = BigDecimal("20.00"),
    product: Product? = null,
  ): DocumentLine {
    val line = DocumentLine(type, documentId, designation)
    line.product = product
    line.quantity = quantity
    line.unitPriceExclTax = unitPrice
    line.discountPercent = BigDecimal.ZERO
    line.vatRate = vatRate
    line.position = 0
    line.recalculate()
    return documentLineRepository.saveAndFlush(line)
  }

  fun createMtoProduct(ref: String): Product {
    val product = Product(ref, "MTO $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = true
    product.sellingPriceExclTax = BigDecimal("100.00")
    product.purchasePriceExclTax = BigDecimal("60.00")
    return productRepository.saveAndFlush(product)
  }

  fun createRegularProduct(ref: String): Product {
    val product = Product(ref, "Regular $ref")
    product.type = Product.ProductType.PRODUCT
    product.mto = false
    product.sellingPriceExclTax = BigDecimal("50.00")
    product.purchasePriceExclTax = BigDecimal("30.00")
    return productRepository.saveAndFlush(product)
  }
}
```

- [ ] **Step 2: Update OrderAServiceTest to use TestDataFactory**

Replace `private fun createClient(code: String)` with `@Autowired lateinit var testData: TestDataFactory` and update all `createClient(...)` calls to `testData.createClient(...)`.

The `createOrderA` method stays local since it's specific to this test class.

- [ ] **Step 3: Update SalesAServiceTest to use TestDataFactory**

Replace `createClient` and `createDocumentLine` with calls to `testData.createClient(code, "123 Billing St", "456 Shipping Ave")` and `testData.createDocumentLine(...)`. Remove the private helper methods. The `createSalesA` method stays local.

Remove `@Autowired lateinit var clientRepository: ClientRepository` and `@Autowired lateinit var productRepository: ProductRepository` if they are only used by the extracted methods.

- [ ] **Step 4: Update SalesBServiceTest to use TestDataFactory**

Replace `createClient`, `createDocumentLine`, `createMtoProduct`, `createRegularProduct` with `testData.*` calls. Remove the private helper methods. The `createSalesAWithOrder` method stays local.

Remove `@Autowired` for repositories only used by extracted helpers.

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/test/kotlin/fr/axl/lvy/TestDataFactory.kt src/test/kotlin/fr/axl/lvy/order/OrderAServiceTest.kt src/test/kotlin/fr/axl/lvy/sale/SalesAServiceTest.kt src/test/kotlin/fr/axl/lvy/sale/SalesBServiceTest.kt
git commit -m "refactor: extract shared test helpers into TestDataFactory"
```
