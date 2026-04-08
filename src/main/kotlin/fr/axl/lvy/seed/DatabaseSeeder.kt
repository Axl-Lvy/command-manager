package fr.axl.lvy.seed

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.order.OrderA
import fr.axl.lvy.order.OrderAService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.sale.SalesA
import fr.axl.lvy.sale.SalesAService
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
 * 1. Users
 * 2. Clients (with contacts)
 * 3. Products
 * 4. Orders A (via [OrderAService] — number sequence auto-generated)
 * 5. Sales A (via [SalesAService] — a VALIDATED sale with an MTO product automatically triggers the
 *    creation of OrderA, SalesB, and OrderB)
 */
@Component
@Profile("seed")
class DatabaseSeeder(
  private val userRepository: UserRepository,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val orderAService: OrderAService,
  private val salesAService: SalesAService,
) : ApplicationRunner {

  private val logger = LoggerFactory.getLogger(DatabaseSeeder::class.java)

  override fun run(args: ApplicationArguments) {
    logger.info("[Seeder] Starting database seeding...")

    seedUsers()
    val clients = seedClients()
    val products = seedProducts()
    seedOrdersA(clients, products)
    seedSalesA(clients, products)

    logger.info("[Seeder] Database seeding complete.")
  }

  private fun seedUsers() {
    logger.info("[Seeder] Creating users...")
    val alice =
      User("Alice Admin", "alice@example.com", "password").apply {
        role = User.Role.ADMIN
        companyId = User.Company.AB
      }
    val bob =
      User("Bob Collab", "bob@example.com", "password").apply {
        role = User.Role.COLLABORATOR
        companyId = User.Company.A
      }
    val charlie =
      User("Charlie Accountant", "charlie@example.com", "password").apply {
        role = User.Role.ACCOUNTANT
        companyId = User.Company.B
      }
    userRepository.saveAll(listOf(alice, bob, charlie))
    logger.info("[Seeder] Created 3 users")
  }

  private fun seedClients(): List<Client> {
    logger.info("[Seeder] Creating clients...")

    val acme =
      Client(name = "Acme Corp").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.A
        email = "contact@acme.com"
        phone = "+33 1 23 45 67 89"
        siret = "12345678901234"
        billingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        shippingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        paymentDelay = 30
        paymentMethod = "Virement"
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
        visibleCompany = User.Company.B
        email = "info@dupont.fr"
        phone = "+33 4 56 78 90 12"
        billingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
        shippingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
        paymentDelay = 45
        paymentMethod = "Virement"
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
        visibleCompany = User.Company.AB
        email = "orders@nestsupplier.com"
        phone = "+33 5 67 89 01 23"
        billingAddress = "20 ZI Nord\n59000 Lille\nFrance"
        paymentDelay = 60
      }
    val savedNest = clientService.save(nestSupplier)
    logger.info("[Seeder] Created client: ${savedNest.name} (${savedNest.clientCode})")

    val jeanDupont =
      Client(name = "Dupont").apply {
        type = Client.ClientType.INDIVIDUAL
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.A
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

  private fun seedOrdersA(clients: List<Client>, products: List<Product>) {
    logger.info("[Seeder] Creating orders A...")
    val acme = clients[0]
    val dupont = clients[1]
    val widgetA = products[0]
    val widgetB = products[1]
    val consulting = products[3]

    // Order 1: Acme, CONFIRMED, standard widgets
    val order1 =
      OrderA("", acme, LocalDate.now().minusDays(10)).apply {
        subject = "Standard widget order"
        vatRate = BigDecimal("20.00")
        clientReference = "PO-2024-001"
        expectedDeliveryDate = LocalDate.now().plusDays(14)
      }
    val order1Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_A, 0L, widgetA, acme).apply {
          quantity = BigDecimal("10")
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_A, 0L, widgetB, acme).apply {
          quantity = BigDecimal("5")
        },
      )
    val savedOrder1 = orderAService.saveWithLines(order1, order1Lines)
    logger.info("[Seeder] Created order A: ${savedOrder1.orderNumber} for ${acme.name} (CONFIRMED)")

    // Order 2: Dupont, IN_PRODUCTION, widgets + consulting
    val order2 =
      OrderA("", dupont, LocalDate.now().minusDays(20)).apply {
        subject = "Widgets + consulting mission"
        vatRate = BigDecimal("20.00")
        expectedDeliveryDate = LocalDate.now().plusDays(7)
      }
    val order2Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_A, 0L, widgetA, dupont).apply {
          quantity = BigDecimal("3")
          discountPercent = BigDecimal("10.00")
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_A, 0L, consulting, dupont).apply {
          quantity = BigDecimal("8")
        },
      )
    var savedOrder2 = orderAService.saveWithLines(order2, order2Lines)
    savedOrder2 = orderAService.changeStatus(savedOrder2, OrderA.OrderAStatus.IN_PRODUCTION)
    logger.info(
      "[Seeder] Created order A: ${savedOrder2.orderNumber} for ${dupont.name} (IN_PRODUCTION)"
    )
  }

  private fun seedSalesA(clients: List<Client>, products: List<Product>) {
    logger.info("[Seeder] Creating sales A...")
    val acme = clients[0]
    val jeanDupont = clients[3]
    val widgetA = products[0]
    val widgetB = products[1]
    val customPart = products[2]

    // Sale 1: Acme, DRAFT, non-MTO → generates OrderA only
    val sale1 =
      SalesA("", acme, LocalDate.now().minusDays(5)).apply {
        subject = "Widget bundle quote"
        vatRate = BigDecimal("20.00")
      }
    val sale1Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_A, 0L, widgetA, acme).apply {
          quantity = BigDecimal("20")
          discountPercent = BigDecimal("5.00")
        },
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_A, 0L, widgetB, acme).apply {
          quantity = BigDecimal("10")
        },
      )
    val savedSale1 = salesAService.saveWithLines(sale1, sale1Lines)
    logger.info(
      "[Seeder] Created sales A: ${savedSale1.saleNumber} for ${acme.name} (DRAFT → generated OrderA)"
    )

    // Sale 2: Jean Dupont, VALIDATED, MTO product → generates OrderA + SalesB + OrderB
    val sale2 =
      SalesA("", jeanDupont, LocalDate.now().minusDays(3)).apply {
        subject = "Custom part order"
        vatRate = BigDecimal("20.00")
        status = SalesA.SalesAStatus.VALIDATED
        expectedDeliveryDate = LocalDate.now().plusDays(10)
      }
    val sale2Lines =
      listOf(
        DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_A, 0L, customPart, jeanDupont)
          .apply { quantity = BigDecimal("1") }
      )
    val savedSale2 = salesAService.saveWithLines(sale2, sale2Lines)
    logger.info(
      "[Seeder] Created sales A: ${savedSale2.saleNumber} for ${jeanDupont.name} (VALIDATED, MTO: generated OrderA + SalesB + OrderB)"
    )
  }
}
