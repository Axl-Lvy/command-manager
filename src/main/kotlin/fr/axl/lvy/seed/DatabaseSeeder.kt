package fr.axl.lvy.seed

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.currency.Currency
import fr.axl.lvy.currency.CurrencyService
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesCodig
import fr.axl.lvy.sale.SalesCodigService
import fr.axl.lvy.sale.SalesStatus
import fr.axl.lvy.user.User
import fr.axl.lvy.user.UserRepository
import java.math.BigDecimal
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Populates the database with representative development data.
 *
 * Active only when the `seed` Spring profile is enabled. Intended to be used alongside
 * `spring.jpa.hibernate.ddl-auto=create` (defined in `application-seed.properties`), which drops
 * and recreates the schema on startup before this runner executes.
 *
 * To activate: run the application with `--spring.profiles.active=local,seed`.
 *
 * Seeded data (in dependency order):
 * 1. Reference data (currencies, payment terms, incoterms, fiscal positions)
 * 2. Users
 * 3. Clients (with contacts)
 * 4. Products
 * 5. Orders A (via [OrderCodigService] — number sequence auto-generated)
 * 6. Sales Codig (via [SalesCodigService] — a VALIDATED sale with an MTO product automatically
 *    triggers the creation of OrderCodig, SalesNetstone, and OrderNetstone)
 */
@Component
@Profile("seed")
class DatabaseSeeder(
  private val userRepository: UserRepository,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val orderCodigService: OrderCodigService,
  private val salesCodigService: SalesCodigService,
  private val currencyService: CurrencyService,
  private val paymentTermService: PaymentTermService,
  private val incotermService: IncotermService,
  private val fiscalPositionService: FiscalPositionService,
) : ApplicationRunner {

  private val logger = LoggerFactory.getLogger(DatabaseSeeder::class.java)

  override fun run(args: ApplicationArguments) {
    logger.info("[Seeder] Starting database seeding...")

    val refData = seedReferenceData()
    seedUsers()
    val clients = seedClients(refData)
    val products = seedProducts()
    seedOrdersCodig(clients, products)
    seedSalesCodig(clients, products, refData)

    logger.info("[Seeder] Database seeding complete.")
  }

  private data class ReferenceData(
    val currencies: List<Currency>,
    val paymentTerms: List<PaymentTerm>,
    val incoterms: List<Incoterm>,
    val fiscalPositions: List<FiscalPosition>,
  )

  private fun seedReferenceData(): ReferenceData {
    logger.info("[Seeder] Creating reference data...")

    val currencies =
      listOf(
          Currency("EUR", "€", "Euro"),
          Currency("USD", "$", "US Dollar"),
          Currency("GBP", "£", "British Pound"),
        )
        .map { currencyService.save(it) }
    logger.info("[Seeder] Created ${currencies.size} currencies")

    val paymentTerms =
      listOf(
          PaymentTerm("30 jours net"),
          PaymentTerm("45 jours fin de mois"),
          PaymentTerm("60 jours net"),
          PaymentTerm("Comptant"),
        )
        .map { paymentTermService.save(it) }
    logger.info("[Seeder] Created ${paymentTerms.size} payment terms")

    val incoterms =
      listOf(
          Incoterm("EXW", "Ex Works"),
          Incoterm("FOB", "Free On Board"),
          Incoterm("CIF", "Cost, Insurance and Freight"),
          Incoterm("DAP", "Delivered At Place"),
          Incoterm("DDP", "Delivered Duty Paid"),
        )
        .map { incotermService.save(it) }
    logger.info("[Seeder] Created ${incoterms.size} incoterms")

    val fiscalPositions =
      listOf(
          FiscalPosition("France métropolitaine"),
          FiscalPosition("Intra-communautaire"),
          FiscalPosition("Export hors UE"),
        )
        .map { fiscalPositionService.save(it) }
    logger.info("[Seeder] Created ${fiscalPositions.size} fiscal positions")

    return ReferenceData(currencies, paymentTerms, incoterms, fiscalPositions)
  }

  private fun seedUsers() {
    logger.info("[Seeder] Creating users...")
    val alice =
      User("Alice Admin", "alice@example.com", "password").apply {
        role = User.Role.ADMIN
        companyId = User.Company.BOTH
      }
    val bob =
      User("Bob Collab", "bob@example.com", "password").apply {
        role = User.Role.COLLABORATOR
        companyId = User.Company.CODIG
      }
    val charlie =
      User("Charlie Accountant", "charlie@example.com", "password").apply {
        role = User.Role.ACCOUNTANT
        companyId = User.Company.NETSTONE
      }
    userRepository.saveAll(listOf(alice, bob, charlie))
    logger.info("[Seeder] Created 3 users")
  }

  private fun seedClients(refData: ReferenceData): List<Client> {
    logger.info("[Seeder] Creating clients...")
    val net30 = refData.paymentTerms[0]
    val net45 = refData.paymentTerms[1]
    val net60 = refData.paymentTerms[2]

    val acme =
      Client(name = "Acme Corp").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.CODIG
        email = "contact@acme.com"
        phone = "+33 1 23 45 67 89"
        siret = "12345678901234"
        billingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        shippingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        paymentDelay = 30
        paymentTerm = net30
        contacts.add(
          Contact(this, "Martin").apply {
            firstName = "Jean"
            email = "jean.martin@acme.com"
            phone = "+33 1 23 45 67 90"
            role = Contact.ContactRole.PRIMARY
            jobTitle = "Purchasing Manager"
          }
        )
      }
    val savedAcme = clientService.save(acme)
    logger.info("[Seeder] Created client: ${savedAcme.name} (${savedAcme.clientCode})")

    val dupont =
      Client(name = "Dupont SARL").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.NETSTONE
        email = "info@dupont.fr"
        phone = "+33 4 56 78 90 12"
        billingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
        shippingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
        paymentDelay = 45
        paymentTerm = net45
        defaultDiscount = BigDecimal("5.00")
        contacts.add(
          Contact(this, "Durand").apply {
            firstName = "Marie"
            email = "m.durand@dupont.fr"
            role = Contact.ContactRole.BILLING
            jobTitle = "Comptable"
          }
        )
      }
    val savedDupont = clientService.save(dupont)
    logger.info("[Seeder] Created client: ${savedDupont.name} (${savedDupont.clientCode})")

    val nestSupplier =
      Client(name = "NestSupplier SAS").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.PRODUCER
        visibleCompany = User.Company.BOTH
        email = "orders@nestsupplier.com"
        phone = "+33 5 67 89 01 23"
        billingAddress = "20 ZI Nord\n59000 Lille\nFrance"
        paymentDelay = 60
        paymentTerm = net60
      }
    val savedNest = clientService.save(nestSupplier)
    logger.info("[Seeder] Created client: ${savedNest.name} (${savedNest.clientCode})")

    val jeanDupont =
      Client(name = "Dupont").apply {
        type = Client.ClientType.INDIVIDUAL
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.CODIG
        email = "jean.dupont@gmail.com"
        phone = "+33 6 12 34 56 78"
        billingAddress = "3 impasse des Roses\n31000 Toulouse\nFrance"
        shippingAddress = "3 impasse des Roses\n31000 Toulouse\nFrance"
      }
    val savedJean = clientService.save(jeanDupont)
    logger.info("[Seeder] Created client: ${savedJean.name} (${savedJean.clientCode})")

    return listOf(savedAcme, savedDupont, savedNest, savedJean)
  }

  private fun seedProducts(): List<Product> {
    logger.info("[Seeder] Creating products...")

    val widgetA =
      Product(name = "Widget A").apply {
        type = Product.ProductType.PRODUCT
        mto = false
        sellingPriceExclTax = BigDecimal("49.99")
        purchasePriceExclTax = BigDecimal("25.00")
        unit = "pcs"
      }
    val savedWidgetA = productService.save(widgetA)
    logger.info("[Seeder] Created product: ${savedWidgetA.name} (${savedWidgetA.reference})")

    val widgetB =
      Product(name = "Widget B").apply {
        type = Product.ProductType.PRODUCT
        mto = false
        sellingPriceExclTax = BigDecimal("89.99")
        purchasePriceExclTax = BigDecimal("45.00")
        unit = "pcs"
      }
    val savedWidgetB = productService.save(widgetB)
    logger.info("[Seeder] Created product: ${savedWidgetB.name} (${savedWidgetB.reference})")

    val customPart =
      Product(name = "Custom Part X").apply {
        type = Product.ProductType.PRODUCT
        mto = true
        sellingPriceExclTax = BigDecimal("199.00")
        purchasePriceExclTax = BigDecimal("120.00")
        unit = "pcs"
        specifications = "Custom manufactured part, 7-day lead time"
        hsCode = "8479.90"
        madeIn = "France"
      }
    val savedCustomPart = productService.save(customPart)
    logger.info("[Seeder] Created product: ${savedCustomPart.name} (${savedCustomPart.reference})")

    val consulting =
      Product(name = "Consulting").apply {
        type = Product.ProductType.SERVICE
        sellingPriceExclTax = BigDecimal("150.00")
        purchasePriceExclTax = BigDecimal.ZERO
        unit = "h"
      }
    val savedConsulting = productService.save(consulting)
    logger.info("[Seeder] Created product: ${savedConsulting.name} (${savedConsulting.reference})")

    val installation =
      Product(name = "Installation").apply {
        type = Product.ProductType.SERVICE
        sellingPriceExclTax = BigDecimal("75.00")
        purchasePriceExclTax = BigDecimal.ZERO
        unit = "h"
      }
    val savedInstallation = productService.save(installation)
    logger.info(
      "[Seeder] Created product: ${savedInstallation.name} (${savedInstallation.reference})"
    )

    return listOf(savedWidgetA, savedWidgetB, savedCustomPart, savedConsulting, savedInstallation)
  }

  private fun seedOrdersCodig(clients: List<Client>, products: List<Product>) {
    logger.info("[Seeder] Creating orders Codig...")
    val acme = clients[0]
    val dupont = clients[1]
    val widgetA = products[0]
    val widgetB = products[1]
    val consulting = products[3]
    val vatRate = BigDecimal("20.00")

    // Order 1: Acme, CONFIRMED, standard widgets
    val order1 =
      OrderCodig("", acme, LocalDate.now().minusDays(10)).apply {
        subject = "Standard widget order"
        this.vatRate = vatRate
        clientReference = "PO-2024-001"
        expectedDeliveryDate = LocalDate.now().plusDays(14)
        incoterms = "DAP"
        incotermLocation = "Paris"
      }
    val order1Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_CODIG, 0L, widgetA, acme).apply {
          quantity = BigDecimal("10")
          this.vatRate = vatRate
          recalculate()
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_CODIG, 0L, widgetB, acme).apply {
          quantity = BigDecimal("5")
          this.vatRate = vatRate
          recalculate()
        },
      )
    val savedOrder1 = orderCodigService.saveWithLines(order1, order1Lines)
    logger.info(
      "[Seeder] Created order Codig: ${savedOrder1.orderNumber} for ${acme.name} (CONFIRMED)"
    )

    // Order 2: Dupont, CONFIRMED, widgets + consulting
    val order2 =
      OrderCodig("", dupont, LocalDate.now().minusDays(20)).apply {
        subject = "Widgets + consulting mission"
        this.vatRate = vatRate
        expectedDeliveryDate = LocalDate.now().plusDays(7)
      }
    val order2Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_CODIG, 0L, widgetA, dupont).apply {
          quantity = BigDecimal("3")
          discountPercent = BigDecimal("10.00")
          this.vatRate = vatRate
          recalculate()
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_CODIG, 0L, consulting, dupont)
          .apply {
            quantity = BigDecimal("8")
            this.vatRate = vatRate
            recalculate()
          },
      )
    var savedOrder2 = orderCodigService.saveWithLines(order2, order2Lines)
    savedOrder2 = orderCodigService.changeStatus(savedOrder2, OrderCodig.OrderCodigStatus.CONFIRMED)
    logger.info(
      "[Seeder] Created order Codig: ${savedOrder2.orderNumber} for ${dupont.name} (CONFIRMED)"
    )
  }

  private fun seedSalesCodig(
    clients: List<Client>,
    products: List<Product>,
    refData: ReferenceData,
  ) {
    logger.info("[Seeder] Creating sales Codig...")
    val acme = clients[0]
    val jeanDupont = clients[3]
    val widgetA = products[0]
    val widgetB = products[1]
    val customPart = products[2]
    val vatRate = BigDecimal("20.00")

    // Sale 1: Acme, DRAFT, non-MTO → generates OrderCodig only
    val sale1 =
      SalesCodig("", acme, LocalDate.now().minusDays(5)).apply {
        subject = "Widget bundle quote"
        paymentTerm = refData.paymentTerms[0]
        incoterms = "DAP"
        incotermLocation = "Paris"
      }
    val sale1Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_CODIG, 0L, widgetA, acme).apply {
          quantity = BigDecimal("20")
          discountPercent = BigDecimal("5.00")
          this.vatRate = vatRate
          recalculate()
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_CODIG, 0L, widgetB, acme).apply {
          quantity = BigDecimal("10")
          this.vatRate = vatRate
          recalculate()
        },
      )
    val savedSale1 = salesCodigService.saveWithLines(sale1, sale1Lines)
    logger.info(
      "[Seeder] Created sales Codig: ${savedSale1.saleNumber} for ${acme.name} (DRAFT → generated OrderCodig)"
    )

    // Sale 2: Jean Dupont, VALIDATED, MTO product → generates OrderCodig + SalesNetstone +
    // OrderNetstone
    val sale2 =
      SalesCodig("", jeanDupont, LocalDate.now().minusDays(3)).apply {
        subject = "Custom part order"
        status = SalesStatus.VALIDATED
        expectedDeliveryDate = LocalDate.now().plusDays(10)
      }
    val sale2Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_CODIG, 0L, customPart, jeanDupont)
          .apply {
            quantity = BigDecimal("1")
            this.vatRate = vatRate
            recalculate()
          }
      )
    val savedSale2 = salesCodigService.saveWithLines(sale2, sale2Lines)
    logger.info(
      "[Seeder] Created sales Codig: ${savedSale2.saleNumber} for ${jeanDupont.name} (VALIDATED, MTO: generated OrderCodig + SalesNetstone + OrderNetstone)"
    )
  }
}
