package fr.axl.lvy.seed

import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.client.deliveryaddress.ClientDeliveryAddress
import fr.axl.lvy.currency.Currency
import fr.axl.lvy.currency.CurrencyService
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.paymentterm.PaymentTerm
import fr.axl.lvy.paymentterm.PaymentTermService
import fr.axl.lvy.product.Product
import fr.axl.lvy.product.ProductService
import fr.axl.lvy.user.User
import fr.axl.lvy.user.UserRepository
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Seeds a lightweight local dataset: reference tables, users, companies/clients, and products.
 *
 * The runner is active only on `test` and `dev`. It intentionally skips orders, sales, deliveries,
 * and invoices to keep local resets fast and easier to maintain.
 */
@Component
@Profile("test", "dev")
class DatabaseSeeder(
  private val userRepository: UserRepository,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val currencyService: CurrencyService,
  private val paymentTermService: PaymentTermService,
  private val incotermService: IncotermService,
  private val fiscalPositionService: FiscalPositionService,
) : ApplicationRunner {

  private val logger = LoggerFactory.getLogger(DatabaseSeeder::class.java)

  override fun run(args: ApplicationArguments) {
    if (currencyService.findAll().isNotEmpty()) {
      logger.info("[Seeder] Database already seeded, skipping.")
      return
    }

    logger.info("[Seeder] Starting database seeding...")

    val refData = seedReferenceData()
    seedUsers()
    seedClients(refData)
    seedProducts()

    logger.info("[Seeder] Database seeding complete.")
  }

  private data class ReferenceData(
    val paymentTerms: List<PaymentTerm>,
    val incoterms: List<Incoterm>,
    val fiscalPositions: List<FiscalPosition>,
  )

  private fun seedReferenceData(): ReferenceData {
    logger.info("[Seeder] Creating reference data...")

    val currencies =
      listOf(
          Currency("EUR", "EUR", "Euro"),
          Currency("USD", "USD", "US Dollar"),
          Currency("GBP", "GBP", "British Pound"),
          Currency("CHF", "CHF", "Swiss Franc"),
          Currency("JPY", "JPY", "Japanese Yen"),
          Currency("CNY", "CNY", "Chinese Yuan"),
        )
        .map(currencyService::save)
    logger.info("[Seeder] Created ${currencies.size} currencies")

    val paymentTerms =
      listOf(
          PaymentTerm("30 jours net"),
          PaymentTerm("45 jours fin de mois"),
          PaymentTerm("60 jours net"),
          PaymentTerm("150 days"),
        )
        .map(paymentTermService::save)
    logger.info("[Seeder] Created ${paymentTerms.size} payment terms")

    val incoterms =
      listOf(
          Incoterm("CFR", "Cost and Freight"),
          Incoterm("FOB", "Free On Board"),
          Incoterm("CIF", "Cost, Insurance and Freight"),
          Incoterm("DAP", "Delivered At Place"),
          Incoterm("DDP", "Delivered Duty Paid"),
          Incoterm("FCA", "Free Carrier"),
          Incoterm("CPT", "Carriage Paid To"),
          Incoterm("CIP", "Carriage and Insurance Paid To"),
        )
        .map(incotermService::save)
    logger.info("[Seeder] Created ${incoterms.size} incoterms")

    val fiscalPositions =
      listOf(
          FiscalPosition("France métropolitaine"),
          FiscalPosition("Intra-communautaire"),
          FiscalPosition("Export hors UE"),
          FiscalPosition("DOM-TOM"),
          FiscalPosition("T1-material"),
        )
        .map(fiscalPositionService::save)
    logger.info("[Seeder] Created ${fiscalPositions.size} fiscal positions")

    return ReferenceData(paymentTerms, incoterms, fiscalPositions)
  }

  private fun seedUsers() {
    logger.info("[Seeder] Creating users...")
    val users =
      listOf(
        User("Alice Admin", "alice@example.com", "password").apply {
          role = User.Role.ADMIN
          companyId = User.Company.BOTH
        },
        User("Bob Collab", "bob@example.com", "password").apply {
          role = User.Role.COLLABORATOR
          companyId = User.Company.CODIG
        },
        User("Charlie Accountant", "charlie@example.com", "password").apply {
          role = User.Role.ACCOUNTANT
          companyId = User.Company.NETSTONE
        },
        User("Diana Manager", "diana@example.com", "password").apply {
          role = User.Role.ADMIN
          companyId = User.Company.BOTH
        },
        User("Eve Sales", "eve@example.com", "password").apply {
          role = User.Role.COLLABORATOR
          companyId = User.Company.CODIG
        },
        User("Frank Buyer", "frank@example.com", "password").apply {
          role = User.Role.COLLABORATOR
          companyId = User.Company.NETSTONE
        },
        User("Grace Finance", "grace@example.com", "password").apply {
          role = User.Role.ACCOUNTANT
          companyId = User.Company.BOTH
        },
        User("Henri Logistic", "henri@example.com", "password").apply {
          role = User.Role.COLLABORATOR
          companyId = User.Company.CODIG
        },
      )
    userRepository.saveAll(users)
    logger.info("[Seeder] Created ${users.size} users")
  }

  private fun seedClients(refData: ReferenceData) {
    logger.info("[Seeder] Creating clients...")

    val net30 = refData.paymentTerms[0]
    val net45 = refData.paymentTerms[1]
    val net150 = refData.paymentTerms[3]
    val cfr = refData.incoterms.first { it.name == "CFR" }
    val cif = refData.incoterms.first { it.name == "CIF" }
    val ddp = refData.incoterms.first { it.name == "DDP" }
    val franceFiscalPosition =
      refData.fiscalPositions.first { it.position == "France métropolitaine" }
    val exportFiscalPosition = refData.fiscalPositions.first { it.position == "Export hors UE" }
    val t1tFiscalPosition = refData.fiscalPositions.first { it.position == "T1-material" }

    val companies =
      listOf(
        Client(name = "Codig").apply {
          type = Client.ClientType.OWN_COMPANY
          role = Client.ClientRole.OWN_COMPANY
          visibleCompany = User.Company.CODIG
          email = "contact@codig.fr"
          phone = "+33 1 40 00 00 01"
          incoterm = cfr
          fiscalPosition = exportFiscalPosition
          paymentTerm = net30
          billingAddress = "39 - 41, rue du Jeu des Enfants\n67000 Strasbourg\nFrance"
          deliveryAddresses.add(
            ClientDeliveryAddress(this, "Codig, Charleston Harbor", "Codig, Charleston Harbor")
              .apply { defaultAddress = true }
          )
          deliveryAddresses.add(
            ClientDeliveryAddress(this, "Codig, Port du Havre", "Codig, Port du Havre")
          )
        },
        Client(name = "Netstone").apply {
          type = Client.ClientType.OWN_COMPANY
          role = Client.ClientRole.OWN_COMPANY
          visibleCompany = User.Company.NETSTONE
          email = "contact@netstone.fr"
          paymentTerm = net30
          incoterm = cfr
          fiscalPosition = exportFiscalPosition
          billingAddress =
            "10/F., Guangdong Investment Tower\n148 Connaught Road\nCentral, Hong Kong"
        },
        Client(name = "THE DOW CHEMICAL COMPANY").apply {
          type = Client.ClientType.COMPANY
          role = Client.ClientRole.CLIENT
          visibleCompany = User.Company.CODIG
          email = "contact@dow.com"
          phone = "+1 989-636-1000"
          billingAddress =
            "ROHM AND HAAS CHEMICALS LLC\n100 Independence Mall West\nPhiladelphia, PA 19106\nUSA"
          paymentDelay = 30
          paymentTerm = net150
          incoterm = ddp
          incotermLocation = "KNX"
          deliveryPort = "Charleston Harbor"
          fiscalPosition = t1tFiscalPosition
          defaultDiscount = BigDecimal("2.00")
          deliveryAddresses.add(
            ClientDeliveryAddress(
                this,
                "THE DOW CHEMICAL COMPANY KNX",
                "730 Dale Avenue\nKnoxville, TN 37921\nUSA",
              )
              .apply { defaultAddress = true }
          )
          contacts.add(
            Contact(this, "Martin").apply {
              firstName = "Jean"
              email = "jean.martin@dow.com"
              role = Contact.ContactRole.PRIMARY
              jobTitle = "Purchasing Manager"
            }
          )
        },
        Client(name = "Dupont").apply {
          type = Client.ClientType.COMPANY
          role = Client.ClientRole.CLIENT
          visibleCompany = User.Company.NETSTONE
          email = "info@dupont.fr"
          phone = "+33 4 56 78 90 12"
          billingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
          shippingAddress = "5 avenue de Lyon\n69001 Lyon\nFrance"
          paymentDelay = 45
          paymentTerm = net45
          incoterm = cfr
          incotermLocation = "Lyon"
          deliveryPort = "Lyon"
          fiscalPosition = franceFiscalPosition
          defaultDiscount = BigDecimal("5.00")
        },
        Client(name = "ALL PLUS THAILAND").apply {
          type = Client.ClientType.COMPANY
          role = Client.ClientRole.PRODUCER
          visibleCompany = User.Company.CODIG
          email = "contact@allplus.co.th"
          billingAddress =
            "999/11 Moo 3, Banchang Subdistrict\nUthai District\nAyutthaya Province\nThailand"
          paymentDelay = 15
          paymentTerm = net150
        },
        Client(name = "ALL PLUS CHINA").apply {
          type = Client.ClientType.COMPANY
          role = Client.ClientRole.PRODUCER
          visibleCompany = User.Company.BOTH
          email = "export@allplus.cn"
          billingAddress = "188 Guangzhou Road\nNanjing\nChina"
          notes = "Lead time: 45 days. FOB Shanghai."
        },
      )

    companies.forEach {
      val saved = clientService.save(it)
      logger.info("[Seeder] Created client: ${saved.name} (${saved.clientCode})")
    }
    logger.info("[Seeder] Created ${companies.size} clients")
  }

  private fun seedProducts() {
    logger.info("[Seeder] Creating products...")

    val clientsByName = clientService.findAll().associateBy { it.name }
    val dowChemical = checkNotNull(clientsByName["THE DOW CHEMICAL COMPANY"])
    val dupont = checkNotNull(clientsByName["Dupont"])

    val products =
      listOf(
        Product(name = "CO_3104 Chine").apply {
          type = Product.ProductType.PRODUCT
          mto = true
          specifications = "AMPS 2403 MONOMER, NEA SAP R3 SPECIFICATION: 1007623"
          sellingPriceExclTax = BigDecimal("49.99")
          sellingCurrency = "USD"
          purchasePriceExclTax = BigDecimal("25.00")
          purchaseCurrency = "USD"
          unit = "Mt"
          hsCode = "9031.80"
          madeIn = "China"
          replaceClientProductCodes(
            listOf(dowChemical to "DOW-CO3104-CN", dupont to "DUP-CO3104-CN")
          )
        },
        Product(name = "CO_3104 TH").apply {
          type = Product.ProductType.PRODUCT
          mto = true
          specifications = "AMPS 2403 MONOMER, NEA SAP R3 SPECIFICATION: 1007623"
          sellingPriceExclTax = BigDecimal("89.99")
          sellingCurrency = "USD"
          purchasePriceExclTax = BigDecimal("45.00")
          purchaseCurrency = "USD"
          unit = "Mt"
          hsCode = "9031.80.234"
          madeIn = "Thailand"
          replaceClientProductCodes(
            listOf(dowChemical to "DOW-CO3104-TH", dupont to "DUP-CO3104-TH")
          )
        },
        Product(name = "Demurrage").apply {
          type = Product.ProductType.SERVICE
          sellingPriceExclTax = BigDecimal("150.00")
          sellingCurrency = "USD"
          purchasePriceExclTax = BigDecimal.ZERO
          purchaseCurrency = "USD"
        },
      )

    products.forEach {
      val saved = productService.save(it)
      logger.info("[Seeder] Created product: ${saved.name} (${saved.reference})")
    }
    logger.info("[Seeder] Created ${products.size} products")
  }
}
