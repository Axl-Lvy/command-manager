package fr.axl.lvy.seed

import fr.axl.lvy.base.NumberSequenceService
import fr.axl.lvy.client.Client
import fr.axl.lvy.client.ClientService
import fr.axl.lvy.client.contact.Contact
import fr.axl.lvy.currency.Currency
import fr.axl.lvy.currency.CurrencyService
import fr.axl.lvy.delivery.DeliveryNoteCodig
import fr.axl.lvy.delivery.DeliveryNoteCodigService
import fr.axl.lvy.delivery.DeliveryNoteNetstone
import fr.axl.lvy.delivery.DeliveryNoteNetstoneRepository
import fr.axl.lvy.documentline.DocumentLine
import fr.axl.lvy.fiscalposition.FiscalPosition
import fr.axl.lvy.fiscalposition.FiscalPositionService
import fr.axl.lvy.incoterm.Incoterm
import fr.axl.lvy.incoterm.IncotermService
import fr.axl.lvy.invoice.InvoiceCodig
import fr.axl.lvy.invoice.InvoiceCodigRepository
import fr.axl.lvy.invoice.InvoiceNetstone
import fr.axl.lvy.invoice.InvoiceNetstoneRepository
import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigService
import fr.axl.lvy.order.OrderNetstone
import fr.axl.lvy.order.OrderNetstoneService
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
 * Active only when the `test` or `dev` Spring profile is enabled. Intended to be used alongside
 * `spring.jpa.hibernate.ddl-auto=create` (defined in `application-test.properties`), which drops
 * and recreates the schema on startup before this runner executes.
 *
 * To activate: run the application with `--spring.profiles.active=local,test`.
 *
 * Seeded data (in dependency order):
 * 1. Reference data: 6 currencies, 7 payment terms, 8 incoterms, 5 fiscal positions
 * 2. Users (8)
 * 3. Clients (12, including an OWN_COMPANY record so MTO sales can resolve a default supplier)
 * 4. Products (14: physical, service, and MTO variants)
 * 5. Orders Codig (~20 across all statuses) with delivery notes and invoices
 * 6. Netstone orders (4) with delivery notes and supplier invoices
 * 7. Sales Codig (12 across all statuses); VALIDATED + MTO sales auto-generate the OrderCodig →
 *    SalesNetstone chain
 */
@Component
@Profile("test", "dev")
class DatabaseSeeder(
  private val userRepository: UserRepository,
  private val clientService: ClientService,
  private val productService: ProductService,
  private val orderCodigService: OrderCodigService,
  private val orderNetstoneService: OrderNetstoneService,
  private val salesCodigService: SalesCodigService,
  private val currencyService: CurrencyService,
  private val paymentTermService: PaymentTermService,
  private val incotermService: IncotermService,
  private val fiscalPositionService: FiscalPositionService,
  private val deliveryNoteCodigService: DeliveryNoteCodigService,
  private val deliveryNoteNetstoneRepository: DeliveryNoteNetstoneRepository,
  private val invoiceCodigRepository: InvoiceCodigRepository,
  private val invoiceNetstoneRepository: InvoiceNetstoneRepository,
  private val numberSequenceService: NumberSequenceService,
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
    val clients = seedClients(refData)
    val products = seedProducts()
    val mtoOrders = seedOrdersCodig(clients, products, refData)
    seedOrdersNetstone(mtoOrders, clients, products)
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
          Currency("CHF", "CHF", "Swiss Franc"),
          Currency("JPY", "¥", "Japanese Yen"),
          Currency("CNY", "¥", "Chinese Yuan"),
        )
        .map { currencyService.save(it) }
    logger.info("[Seeder] Created ${currencies.size} currencies")

    val paymentTerms =
      listOf(
          PaymentTerm("30 jours net"),
          PaymentTerm("45 jours fin de mois"),
          PaymentTerm("60 jours net"),
          PaymentTerm("Comptant"),
          PaymentTerm("15 jours net"),
          PaymentTerm("60 jours fin de mois"),
          PaymentTerm("Acompte 30% + solde à la livraison"),
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
          Incoterm("FCA", "Free Carrier"),
          Incoterm("CPT", "Carriage Paid To"),
          Incoterm("CIP", "Carriage and Insurance Paid To"),
        )
        .map { incotermService.save(it) }
    logger.info("[Seeder] Created ${incoterms.size} incoterms")

    val fiscalPositions =
      listOf(
          FiscalPosition("France métropolitaine"),
          FiscalPosition("Intra-communautaire"),
          FiscalPosition("Export hors UE"),
          FiscalPosition("DOM-TOM"),
          FiscalPosition("Suisse"),
        )
        .map { fiscalPositionService.save(it) }
    logger.info("[Seeder] Created ${fiscalPositions.size} fiscal positions")

    return ReferenceData(currencies, paymentTerms, incoterms, fiscalPositions)
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

  /**
   * Returns clients in a fixed order used as indices throughout the seeder: 0=Acme, 1=Dupont SARL,
   * 2=NestSupplier, 3=Jean Dupont, 4=TechPro, 5=Meridian, 6=Global Parts, 7=Innovatech, 8=Benali,
   * 9=ChinaSource, 10=Boutique Lefèvre, 11=Netstone SAS (OWN_COMPANY).
   */
  private fun seedClients(refData: ReferenceData): List<Client> {
    logger.info("[Seeder] Creating clients...")
    val net30 = refData.paymentTerms[0]
    val net45 = refData.paymentTerms[1]
    val net60 = refData.paymentTerms[2]
    val cash = refData.paymentTerms[3]
    val net15 = refData.paymentTerms[4]
    val net60eom = refData.paymentTerms[5]
    val deposit30 = refData.paymentTerms[6]

    // OWN_COMPANY record required for the MTO sales chain (findDefaultCodigSupplier)
    val netstone =
      Client(name = "Netstone SAS").apply {
        type = Client.ClientType.OWN_COMPANY
        role = Client.ClientRole.OWN_COMPANY
        visibleCompany = User.Company.NETSTONE
        email = "contact@netstone.fr"
        billingAddress = "1 rue de l'Innovation\n75008 Paris\nFrance"
      }
    val savedNetstone = clientService.save(netstone)
    logger.info("[Seeder] Created client: ${savedNetstone.name} (${savedNetstone.clientCode})")

    val acme =
      Client(name = "Acme Corp").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.CODIG
        email = "contact@acme.com"
        phone = "+33 1 23 45 67 89"
        siret = "12345678901234"
        vatNumber = "FR12345678901"
        billingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        shippingAddress = "10 rue de la Paix\n75001 Paris\nFrance"
        paymentDelay = 30
        paymentTerm = net30
        defaultDiscount = BigDecimal("2.00")
        contacts.add(
          Contact(this, "Martin").apply {
            firstName = "Jean"
            email = "jean.martin@acme.com"
            phone = "+33 1 23 45 67 90"
            role = Contact.ContactRole.PRIMARY
            jobTitle = "Purchasing Manager"
          }
        )
        contacts.add(
          Contact(this, "Bernard").apply {
            firstName = "Sophie"
            email = "s.bernard@acme.com"
            role = Contact.ContactRole.BILLING
            jobTitle = "Comptable"
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

    val techPro =
      Client(name = "TechPro Industries").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.BOTH
        email = "procurement@techpro.eu"
        phone = "+33 3 45 67 89 01"
        siret = "98765432109876"
        vatNumber = "FR98765432109"
        billingAddress = "15 ZAC Technopôle\n38000 Grenoble\nFrance"
        shippingAddress = "15 ZAC Technopôle\n38000 Grenoble\nFrance"
        paymentDelay = 30
        paymentTerm = net30
        deliveryPort = "Grenoble"
        contacts.add(
          Contact(this, "Leroy").apply {
            firstName = "Pierre"
            email = "p.leroy@techpro.eu"
            phone = "+33 3 45 67 89 02"
            role = Contact.ContactRole.PRIMARY
            jobTitle = "Head of Procurement"
          }
        )
        contacts.add(
          Contact(this, "Müller").apply {
            firstName = "Klaus"
            email = "k.muller@techpro.eu"
            role = Contact.ContactRole.TECHNICAL
            jobTitle = "Technical Director"
          }
        )
      }
    val savedTechPro = clientService.save(techPro)
    logger.info("[Seeder] Created client: ${savedTechPro.name} (${savedTechPro.clientCode})")

    val meridian =
      Client(name = "Meridian Trading Ltd").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.CODIG
        email = "orders@meridian.co.uk"
        phone = "+44 20 7123 4567"
        billingAddress = "42 City Road\nLondon EC1V 2JF\nUnited Kingdom"
        shippingAddress = "42 City Road\nLondon EC1V 2JF\nUnited Kingdom"
        paymentDelay = 15
        paymentTerm = net15
        defaultDiscount = BigDecimal("3.00")
        contacts.add(
          Contact(this, "Smith").apply {
            firstName = "James"
            email = "j.smith@meridian.co.uk"
            role = Contact.ContactRole.PRIMARY
            jobTitle = "Purchasing Director"
          }
        )
      }
    val savedMeridian = clientService.save(meridian)
    logger.info("[Seeder] Created client: ${savedMeridian.name} (${savedMeridian.clientCode})")

    val globalParts =
      Client(name = "Global Parts SA").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.PRODUCER
        visibleCompany = User.Company.NETSTONE
        email = "supply@globalparts.fr"
        phone = "+33 2 34 56 78 90"
        billingAddress = "8 rue de l'Industrie\n44000 Nantes\nFrance"
        paymentDelay = 60
        paymentTerm = net60eom
      }
    val savedGlobalParts = clientService.save(globalParts)
    logger.info(
      "[Seeder] Created client: ${savedGlobalParts.name} (${savedGlobalParts.clientCode})"
    )

    val innovatech =
      Client(name = "Innovatech SARL").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.BOTH
        visibleCompany = User.Company.BOTH
        email = "contact@innovatech.fr"
        phone = "+33 4 78 90 12 34"
        siret = "55544433322211"
        billingAddress = "22 boulevard Technologique\n13000 Marseille\nFrance"
        shippingAddress = "22 boulevard Technologique\n13000 Marseille\nFrance"
        paymentDelay = 45
        paymentTerm = net45
        contacts.add(
          Contact(this, "Rossi").apply {
            firstName = "Marco"
            email = "m.rossi@innovatech.fr"
            role = Contact.ContactRole.PRIMARY
            jobTitle = "CEO"
          }
        )
      }
    val savedInnovatech = clientService.save(innovatech)
    logger.info("[Seeder] Created client: ${savedInnovatech.name} (${savedInnovatech.clientCode})")

    val benali =
      Client(name = "Benali").apply {
        type = Client.ClientType.INDIVIDUAL
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.NETSTONE
        email = "sofiane.benali@gmail.com"
        phone = "+33 7 89 01 23 45"
        billingAddress = "12 allée des Pins\n06000 Nice\nFrance"
        shippingAddress = "12 allée des Pins\n06000 Nice\nFrance"
      }
    val savedBenali = clientService.save(benali)
    logger.info("[Seeder] Created client: ${savedBenali.name} (${savedBenali.clientCode})")

    val chinaSource =
      Client(name = "ChinaSource Ltd").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.PRODUCER
        visibleCompany = User.Company.BOTH
        email = "export@chinasource.cn"
        phone = "+86 21 5678 9012"
        billingAddress = "Building A, 100 Pudong Avenue\nShanghai 200120\nChina"
        paymentDelay = 60
        paymentTerm = deposit30
        notes = "Lead time: 45 days. FOB Shanghai."
      }
    val savedChinaSource = clientService.save(chinaSource)
    logger.info(
      "[Seeder] Created client: ${savedChinaSource.name} (${savedChinaSource.clientCode})"
    )

    val boutique =
      Client(name = "Boutique Lefèvre").apply {
        type = Client.ClientType.COMPANY
        role = Client.ClientRole.CLIENT
        visibleCompany = User.Company.CODIG
        email = "boutique@lefevre.fr"
        phone = "+33 1 98 76 54 32"
        siret = "11122233344455"
        billingAddress = "7 rue du Commerce\n75015 Paris\nFrance"
        shippingAddress = "7 rue du Commerce\n75015 Paris\nFrance"
        paymentDelay = 30
        paymentTerm = cash
      }
    val savedBoutique = clientService.save(boutique)
    logger.info("[Seeder] Created client: ${savedBoutique.name} (${savedBoutique.clientCode})")

    logger.info("[Seeder] Created 12 clients")
    return listOf(
      savedAcme,
      savedDupont,
      savedNest,
      savedJean,
      savedTechPro,
      savedMeridian,
      savedGlobalParts,
      savedInnovatech,
      savedBenali,
      savedChinaSource,
      savedBoutique,
      savedNetstone,
    )
  }

  /**
   * Returns products in a fixed order used as indices throughout the seeder: 0=Widget A, 1=Widget
   * B, 2=Custom Part X (MTO), 3=Consulting, 4=Installation, 5=Electronic Module Y, 6=Steel Frame Z
   * (MTO), 7=Maintenance Kit, 8=Training, 9=Technical Support, 10=Industrial Cable 10m, 11=Custom
   * Assembly A (MTO), 12=Packaging Set, 13=Sensor Module.
   */
  private fun seedProducts(): List<Product> {
    logger.info("[Seeder] Creating products...")

    fun save(p: Product): Product {
      val saved = productService.save(p)
      logger.info("[Seeder] Created product: ${saved.name} (${saved.reference})")
      return saved
    }

    return listOf(
      save(
        Product(name = "Widget A").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("49.99")
          purchasePriceExclTax = BigDecimal("25.00")
          unit = "pcs"
        }
      ),
      save(
        Product(name = "Widget B").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("89.99")
          purchasePriceExclTax = BigDecimal("45.00")
          unit = "pcs"
        }
      ),
      save(
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
      ),
      save(
        Product(name = "Consulting").apply {
          type = Product.ProductType.SERVICE
          sellingPriceExclTax = BigDecimal("150.00")
          purchasePriceExclTax = BigDecimal.ZERO
          unit = "h"
        }
      ),
      save(
        Product(name = "Installation").apply {
          type = Product.ProductType.SERVICE
          sellingPriceExclTax = BigDecimal("75.00")
          purchasePriceExclTax = BigDecimal.ZERO
          unit = "h"
        }
      ),
      save(
        Product(name = "Electronic Module Y").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("129.99")
          purchasePriceExclTax = BigDecimal("65.00")
          unit = "pcs"
          hsCode = "8542.31"
          madeIn = "Germany"
        }
      ),
      save(
        Product(name = "Steel Frame Z").apply {
          type = Product.ProductType.PRODUCT
          mto = true
          sellingPriceExclTax = BigDecimal("349.00")
          purchasePriceExclTax = BigDecimal("200.00")
          unit = "pcs"
          specifications = "Custom welded steel frame, 14-day lead time"
          hsCode = "7308.90"
          madeIn = "France"
        }
      ),
      save(
        Product(name = "Maintenance Kit").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("35.00")
          purchasePriceExclTax = BigDecimal("18.00")
          unit = "kit"
        }
      ),
      save(
        Product(name = "Training").apply {
          type = Product.ProductType.SERVICE
          sellingPriceExclTax = BigDecimal("120.00")
          purchasePriceExclTax = BigDecimal.ZERO
          unit = "h"
        }
      ),
      save(
        Product(name = "Technical Support").apply {
          type = Product.ProductType.SERVICE
          sellingPriceExclTax = BigDecimal("95.00")
          purchasePriceExclTax = BigDecimal.ZERO
          unit = "h"
        }
      ),
      save(
        Product(name = "Industrial Cable 10m").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("22.50")
          purchasePriceExclTax = BigDecimal("11.00")
          unit = "m"
          hsCode = "8544.49"
          madeIn = "China"
        }
      ),
      save(
        Product(name = "Custom Assembly A").apply {
          type = Product.ProductType.PRODUCT
          mto = true
          sellingPriceExclTax = BigDecimal("450.00")
          purchasePriceExclTax = BigDecimal("280.00")
          unit = "pcs"
          specifications = "Custom assembly per client tolerances"
          hsCode = "8479.89"
          madeIn = "France"
        }
      ),
      save(
        Product(name = "Packaging Set").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("15.00")
          purchasePriceExclTax = BigDecimal("7.50")
          unit = "set"
        }
      ),
      save(
        Product(name = "Sensor Module").apply {
          type = Product.ProductType.PRODUCT
          mto = false
          sellingPriceExclTax = BigDecimal("75.00")
          purchasePriceExclTax = BigDecimal("38.00")
          unit = "pcs"
          hsCode = "9031.80"
          madeIn = "Japan"
        }
      ),
    )
  }

  /**
   * Builds an order document line from [product] with the given qty, optional discount, and VAT.
   */
  private fun orderLine(
    product: Product,
    client: Client,
    qty: String,
    discount: String = "0.00",
    vat: BigDecimal = BigDecimal("20.00"),
  ): DocumentLine =
    DocumentLine.fromProduct(DocumentLine.DocumentType.ORDER_CODIG, 0L, product, client).apply {
      quantity = BigDecimal(qty)
      discountPercent = BigDecimal(discount)
      vatRate = vat
      recalculate()
    }

  /** Builds a sales document line for SALES_CODIG documents. */
  private fun salesLine(
    product: Product,
    client: Client,
    qty: String,
    discount: String = "0.00",
    vat: BigDecimal = BigDecimal("20.00"),
  ): DocumentLine =
    DocumentLine.fromProduct(DocumentLine.DocumentType.SALES_CODIG, 0L, product, client).apply {
      quantity = BigDecimal(qty)
      discountPercent = BigDecimal(discount)
      vatRate = vat
      recalculate()
    }

  /** Builds a Netstone order document line, using purchase price. */
  private fun netstoneOrderLine(
    product: Product,
    qty: String,
    vat: BigDecimal = BigDecimal("20.00"),
  ): DocumentLine =
    DocumentLine.fromProduct(
        DocumentLine.DocumentType.ORDER_NETSTONE,
        0L,
        product,
        usePurchasePrice = true,
      )
      .apply {
        quantity = BigDecimal(qty)
        vatRate = vat
        recalculate()
      }

  /**
   * Creates ~20 Codig orders covering all statuses. Returns a pair of confirmed orders that contain
   * MTO products — these are passed to [seedOrdersNetstone] to generate supplier orders.
   */
  private fun seedOrdersCodig(
    clients: List<Client>,
    products: List<Product>,
    refData: ReferenceData,
  ): List<OrderCodig> {
    logger.info("[Seeder] Creating orders Codig...")

    val acme = clients[0]
    val dupont = clients[1]
    val jeanDupont = clients[3]
    val techPro = clients[4]
    val meridian = clients[5]
    val innovatech = clients[7]
    val boutique = clients[10]

    val widgetA = products[0]
    val widgetB = products[1]
    val customPartX = products[2]
    val consulting = products[3]
    val installation = products[4]
    val electronicModuleY = products[5]
    val steelFrameZ = products[6]
    val maintenanceKit = products[7]
    val training = products[8]
    val techSupport = products[9]
    val cable10m = products[10]
    val customAssembly = products[11]
    val packagingSet = products[12]
    val sensorModule = products[13]

    // Helper: confirm an order immediately after creation
    fun confirmed(order: OrderCodig, lines: List<DocumentLine>): OrderCodig {
      val saved = orderCodigService.saveWithLines(order, lines)
      return orderCodigService.changeStatus(saved, OrderCodig.OrderCodigStatus.CONFIRMED)
    }

    // Helper: deliver a confirmed order and attach a delivery note
    fun delivered(
      order: OrderCodig,
      client: Client,
      carrier: String,
      tracking: String,
      packages: Int,
    ): OrderCodig {
      val del = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.DELIVERED)
      deliveryNoteCodigService.save(
        DeliveryNoteCodig("", del, client).apply {
          status = DeliveryNoteCodig.DeliveryNoteCodigStatus.DELIVERED
          shippingDate = del.orderDate.plusDays(4)
          deliveryDate = del.orderDate.plusDays(6)
          this.carrier = carrier
          trackingNumber = tracking
          packageCount = packages
          signedBy = "Réceptionnaire"
          signatureDate = del.orderDate.plusDays(6)
        }
      )
      return del
    }

    // Helper: invoice a delivered order and create the invoice record
    fun invoiced(order: OrderCodig, client: Client, daysUntilDue: Long, paid: Boolean): OrderCodig {
      val inv = orderCodigService.changeStatus(order, OrderCodig.OrderCodigStatus.INVOICED)
      val invoiceNum = numberSequenceService.nextNumber("INVOICE_CODIG", "FAC-", 6)
      invoiceCodigRepository.save(
        InvoiceCodig(invoiceNum, client, inv.orderDate.plusDays(7)).apply {
          orderCodig = inv
          clientName = client.name
          clientAddress = client.billingAddress
          clientSiret = client.siret
          currency = inv.currency
          dueDate = inv.orderDate.plusDays(7 + daysUntilDue)
          status =
            if (paid) InvoiceCodig.InvoiceCodigStatus.PAID
            else InvoiceCodig.InvoiceCodigStatus.ISSUED
          if (paid) {
            paymentDate = inv.orderDate.plusDays(7 + daysUntilDue - 5)
          }
          legalNotice = "Pénalités de retard : 3 fois le taux directeur de la BCE."
        }
      )
      return inv
    }

    // ── DRAFT orders ──────────────────────────────────────────────────────────
    orderCodigService.saveWithLines(
      OrderCodig("", acme, LocalDate.now().minusDays(2)).apply {
        subject = "Demande de devis – Widgets standards"
        vatRate = BigDecimal("20.00")
        clientReference = "RFQ-2024-010"
      },
      listOf(orderLine(widgetA, acme, "15"), orderLine(widgetB, acme, "8")),
    )
    logger.info("[Seeder] Created order DRAFT for Acme Corp")

    orderCodigService.saveWithLines(
      OrderCodig("", techPro, LocalDate.now().minusDays(1)).apply {
        subject = "Modules électroniques Q1"
        vatRate = BigDecimal("20.00")
        clientReference = "TP-PO-2024-042"
        expectedDeliveryDate = LocalDate.now().plusDays(21)
        incoterms = "DAP"
        incotermLocation = "Grenoble"
      },
      listOf(orderLine(electronicModuleY, techPro, "20"), orderLine(sensorModule, techPro, "10")),
    )
    logger.info("[Seeder] Created order DRAFT for TechPro")

    orderCodigService.saveWithLines(
      OrderCodig("", meridian, LocalDate.now()).apply {
        subject = "Widget bundle + accessories"
        vatRate = BigDecimal("20.00")
        currency = "GBP"
        incoterms = "DAP"
        incotermLocation = "London"
      },
      listOf(orderLine(widgetA, meridian, "30", "3.00"), orderLine(packagingSet, meridian, "30")),
    )
    logger.info("[Seeder] Created order DRAFT for Meridian")

    // ── CONFIRMED orders ──────────────────────────────────────────────────────
    confirmed(
        OrderCodig("", acme, LocalDate.now().minusDays(15)).apply {
          subject = "Widgets + installation"
          vatRate = BigDecimal("20.00")
          clientReference = "PO-2024-001"
          expectedDeliveryDate = LocalDate.now().plusDays(7)
          incoterms = "DAP"
          incotermLocation = "Paris"
        },
        listOf(
          orderLine(widgetA, acme, "10"),
          orderLine(widgetB, acme, "5"),
          orderLine(installation, acme, "4"),
        ),
      )
      .also { logger.info("[Seeder] Created order CONFIRMED: ${it.orderNumber}") }

    confirmed(
        OrderCodig("", dupont, LocalDate.now().minusDays(20)).apply {
          subject = "Widgets + mission consulting"
          vatRate = BigDecimal("20.00")
          expectedDeliveryDate = LocalDate.now().plusDays(5)
        },
        listOf(orderLine(widgetA, dupont, "3", "10.00"), orderLine(consulting, dupont, "8")),
      )
      .also { logger.info("[Seeder] Created order CONFIRMED: ${it.orderNumber}") }

    confirmed(
        OrderCodig("", techPro, LocalDate.now().minusDays(8)).apply {
          subject = "Modules Y + câbles + support"
          vatRate = BigDecimal("20.00")
          clientReference = "TP-PO-2024-043"
          expectedDeliveryDate = LocalDate.now().plusDays(14)
          incoterms = "DAP"
          incotermLocation = "Grenoble"
        },
        listOf(
          orderLine(electronicModuleY, techPro, "50"),
          orderLine(cable10m, techPro, "100"),
          orderLine(techSupport, techPro, "16"),
        ),
      )
      .also { logger.info("[Seeder] Created order CONFIRMED: ${it.orderNumber}") }

    confirmed(
        OrderCodig("", innovatech, LocalDate.now().minusDays(5)).apply {
          subject = "Formation et maintenance préventive"
          vatRate = BigDecimal("20.00")
        },
        listOf(orderLine(training, innovatech, "2"), orderLine(maintenanceKit, innovatech, "5")),
      )
      .also { logger.info("[Seeder] Created order CONFIRMED: ${it.orderNumber}") }

    confirmed(
        OrderCodig("", boutique, LocalDate.now().minusDays(3)).apply {
          subject = "Stock Widget A"
          vatRate = BigDecimal("20.00")
          clientReference = "BL-PO-2024-007"
        },
        listOf(orderLine(widgetA, boutique, "25", "5.00"), orderLine(packagingSet, boutique, "25")),
      )
      .also { logger.info("[Seeder] Created order CONFIRMED: ${it.orderNumber}") }

    // ── DELIVERED orders ──────────────────────────────────────────────────────
    delivered(
        confirmed(
          OrderCodig("", acme, LocalDate.now().minusDays(40)).apply {
            subject = "Capteurs + câbles H2"
            vatRate = BigDecimal("20.00")
            clientReference = "PO-2024-002"
            expectedDeliveryDate = LocalDate.now().minusDays(30)
          },
          listOf(orderLine(sensorModule, acme, "5"), orderLine(cable10m, acme, "50")),
        ),
        acme,
        "DHL",
        "DHL5691234001",
        2,
      )
      .also { logger.info("[Seeder] Created order DELIVERED: ${it.orderNumber}") }

    delivered(
        confirmed(
          OrderCodig("", dupont, LocalDate.now().minusDays(50)).apply {
            subject = "Kit maintenance annuel"
            vatRate = BigDecimal("20.00")
            expectedDeliveryDate = LocalDate.now().minusDays(40)
          },
          listOf(orderLine(maintenanceKit, dupont, "10"), orderLine(installation, dupont, "6")),
        ),
        dupont,
        "Chronopost",
        "CP8821003456",
        3,
      )
      .also { logger.info("[Seeder] Created order DELIVERED: ${it.orderNumber}") }

    delivered(
        confirmed(
          OrderCodig("", meridian, LocalDate.now().minusDays(35)).apply {
            subject = "Widget A bulk – Q4"
            vatRate = BigDecimal("20.00")
            currency = "GBP"
            clientReference = "MER-2024-019"
            expectedDeliveryDate = LocalDate.now().minusDays(25)
            incoterms = "DAP"
            incotermLocation = "London"
          },
          listOf(
            orderLine(widgetA, meridian, "50", "3.00"),
            orderLine(widgetB, meridian, "20", "3.00"),
          ),
        ),
        meridian,
        "UPS",
        "UPS1Z999AA10123456",
        5,
      )
      .also { logger.info("[Seeder] Created order DELIVERED: ${it.orderNumber}") }

    delivered(
        confirmed(
          OrderCodig("", jeanDupont, LocalDate.now().minusDays(25)).apply {
            subject = "Module capteur particulier"
            vatRate = BigDecimal("20.00")
            expectedDeliveryDate = LocalDate.now().minusDays(18)
          },
          listOf(orderLine(sensorModule, jeanDupont, "2")),
        ),
        jeanDupont,
        "Colissimo",
        "COL99887766554",
        1,
      )
      .also { logger.info("[Seeder] Created order DELIVERED: ${it.orderNumber}") }

    // ── INVOICED orders ───────────────────────────────────────────────────────
    invoiced(
        delivered(
          confirmed(
            OrderCodig("", acme, LocalDate.now().minusDays(90)).apply {
              subject = "Commande annuelle widgets"
              vatRate = BigDecimal("20.00")
              clientReference = "PO-2023-088"
              expectedDeliveryDate = LocalDate.now().minusDays(80)
            },
            listOf(
              orderLine(widgetA, acme, "100", "2.00"),
              orderLine(widgetB, acme, "50", "2.00"),
              orderLine(packagingSet, acme, "150"),
            ),
          ),
          acme,
          "DHL",
          "DHL5690001234",
          10,
        ),
        acme,
        30,
        true,
      )
      .also { logger.info("[Seeder] Created order INVOICED (paid): ${it.orderNumber}") }

    invoiced(
        delivered(
          confirmed(
            OrderCodig("", dupont, LocalDate.now().minusDays(70)).apply {
              subject = "Modules électroniques + consulting"
              vatRate = BigDecimal("20.00")
              expectedDeliveryDate = LocalDate.now().minusDays(60)
            },
            listOf(orderLine(electronicModuleY, dupont, "15"), orderLine(consulting, dupont, "12")),
          ),
          dupont,
          "TNT",
          "TNT456789012",
          4,
        ),
        dupont,
        45,
        true,
      )
      .also { logger.info("[Seeder] Created order INVOICED (paid): ${it.orderNumber}") }

    invoiced(
        delivered(
          confirmed(
            OrderCodig("", techPro, LocalDate.now().minusDays(60)).apply {
              subject = "Capteurs + câbles + formation"
              vatRate = BigDecimal("20.00")
              clientReference = "TP-PO-2024-031"
              expectedDeliveryDate = LocalDate.now().minusDays(50)
              incoterms = "DAP"
              incotermLocation = "Grenoble"
            },
            listOf(
              orderLine(sensorModule, techPro, "30"),
              orderLine(cable10m, techPro, "200"),
              orderLine(training, techPro, "8"),
            ),
          ),
          techPro,
          "Fedex",
          "FDX789012345",
          6,
        ),
        techPro,
        30,
        false,
      )
      .also { logger.info("[Seeder] Created order INVOICED (unpaid): ${it.orderNumber}") }

    invoiced(
        delivered(
          confirmed(
            OrderCodig("", innovatech, LocalDate.now().minusDays(45)).apply {
              subject = "Kits maintenance + support technique"
              vatRate = BigDecimal("20.00")
              expectedDeliveryDate = LocalDate.now().minusDays(35)
            },
            listOf(
              orderLine(maintenanceKit, innovatech, "20"),
              orderLine(techSupport, innovatech, "24"),
            ),
          ),
          innovatech,
          "DHL",
          "DHL5693456789",
          3,
        ),
        innovatech,
        45,
        true,
      )
      .also { logger.info("[Seeder] Created order INVOICED (paid): ${it.orderNumber}") }

    // ── CANCELLED order ───────────────────────────────────────────────────────
    orderCodigService
      .saveWithLines(
        OrderCodig("", boutique, LocalDate.now().minusDays(60)).apply {
          subject = "Commande annulée – Budget revu"
          vatRate = BigDecimal("20.00")
        },
        listOf(orderLine(widgetB, boutique, "10")),
      )
      .let { orderCodigService.changeStatus(it, OrderCodig.OrderCodigStatus.CONFIRMED) }
      .let { orderCodigService.changeStatus(it, OrderCodig.OrderCodigStatus.CANCELLED) }
      .also { logger.info("[Seeder] Created order CANCELLED: ${it.orderNumber}") }

    // ── Confirmed orders with MTO products (returned for OrderNetstone seeding) ──
    val mto1 =
      confirmed(
          OrderCodig("", acme, LocalDate.now().minusDays(12)).apply {
            subject = "Pièces custom X – lot spécial"
            vatRate = BigDecimal("20.00")
            clientReference = "PO-2024-MTO-001"
            expectedDeliveryDate = LocalDate.now().plusDays(10)
          },
          listOf(orderLine(customPartX, acme, "5"), orderLine(packagingSet, acme, "5")),
        )
        .also { logger.info("[Seeder] Created MTO order CONFIRMED: ${it.orderNumber}") }

    val mto2 =
      confirmed(
          OrderCodig("", techPro, LocalDate.now().minusDays(18)).apply {
            subject = "Assemblage sur mesure – Projet Alpha"
            vatRate = BigDecimal("20.00")
            clientReference = "TP-PO-2024-MTO-002"
            expectedDeliveryDate = LocalDate.now().plusDays(20)
            incoterms = "DAP"
            incotermLocation = "Grenoble"
          },
          listOf(orderLine(customAssembly, techPro, "3"), orderLine(techSupport, techPro, "8")),
        )
        .also { logger.info("[Seeder] Created MTO order CONFIRMED: ${it.orderNumber}") }

    val mto3 =
      confirmed(
          OrderCodig("", dupont, LocalDate.now().minusDays(25)).apply {
            subject = "Châssis acier Z – commande spéciale"
            vatRate = BigDecimal("20.00")
            expectedDeliveryDate = LocalDate.now().plusDays(15)
          },
          listOf(orderLine(steelFrameZ, dupont, "2")),
        )
        .also { logger.info("[Seeder] Created MTO order CONFIRMED: ${it.orderNumber}") }

    logger.info("[Seeder] Orders Codig seeding complete")
    return listOf(mto1, mto2, mto3)
  }

  /**
   * Creates Netstone supplier orders linked to the [mtoOrders] confirmed Codig orders, then adds
   * delivery notes and invoices for some of them.
   */
  private fun seedOrdersNetstone(
    mtoOrders: List<OrderCodig>,
    clients: List<Client>,
    products: List<Product>,
  ) {
    logger.info("[Seeder] Creating orders Netstone...")

    val nestSupplier = clients[2]
    val globalParts = clients[6]
    val chinaSource = clients[9]

    val customPartX = products[2]
    val steelFrameZ = products[6]
    val customAssembly = products[11]

    val mto1 = mtoOrders[0] // acme order with Custom Part X
    val mto2 = mtoOrders[1] // techPro order with Custom Assembly
    val mto3 = mtoOrders[2] // dupont order with Steel Frame Z

    // Netstone order 1: RECEIVED (fully processed) — supply for mto1
    val nsOrder1 =
      orderNetstoneService
        .saveWithLines(
          OrderNetstone("", mto1).apply { orderDate = mto1.orderDate.minusDays(1) },
          listOf(netstoneOrderLine(customPartX, "5")),
        )
        .let { orderNetstoneService.changeStatus(it, OrderNetstone.OrderNetstoneStatus.CONFIRMED) }
        .let { orderNetstoneService.markReceived(it, mto1.orderDate.plusDays(8), true, "") }
    logger.info("[Seeder] Created order Netstone RECEIVED: ${nsOrder1.orderNumber}")

    // Delivery note for nsOrder1
    deliveryNoteNetstoneRepository.save(
      DeliveryNoteNetstone(
          numberSequenceService.nextNumber("DELIVERY_NETSTONE", "BL_NST-", 6),
          nsOrder1,
        )
        .apply {
          status = DeliveryNoteNetstone.DeliveryNoteNetstoneStatus.INSPECTED
          shippingDate = mto1.orderDate.minusDays(1).plusDays(5)
          arrivalDate = mto1.orderDate.plusDays(8)
          containerNumber = "FFAU1234567"
          lot = "LOT-2024-001"
        }
    )
    logger.info("[Seeder] Created delivery note Netstone for ${nsOrder1.orderNumber}")

    // Supplier invoice for nsOrder1
    invoiceNetstoneRepository.save(
      InvoiceNetstone(
          numberSequenceService.nextNumber("INVOICE_NETSTONE", "FRN-", 6),
          InvoiceNetstone.RecipientType.PRODUCER,
          nestSupplier,
          mto1.orderDate.plusDays(9),
        )
        .apply {
          supplierInvoiceNumber = "NST-FACT-2024-0042"
          orderNetstone = nsOrder1
          origin = InvoiceNetstone.Origin.ORDER_LINKED
          status = InvoiceNetstone.InvoiceNetstoneStatus.PAID
          dueDate = mto1.orderDate.plusDays(9 + 60)
          paymentDate = mto1.orderDate.plusDays(9 + 55)
        }
    )
    logger.info("[Seeder] Created invoice Netstone for ${nsOrder1.orderNumber}")

    // Netstone order 2: CONFIRMED — supply for mto2
    val nsOrder2 =
      orderNetstoneService
        .saveWithLines(
          OrderNetstone("", mto2).apply { orderDate = mto2.orderDate.minusDays(1) },
          listOf(netstoneOrderLine(customAssembly, "3")),
        )
        .let { orderNetstoneService.changeStatus(it, OrderNetstone.OrderNetstoneStatus.CONFIRMED) }
    logger.info("[Seeder] Created order Netstone CONFIRMED: ${nsOrder2.orderNumber}")

    // Netstone order 3: SENT — supply for mto3
    val nsOrder3 =
      orderNetstoneService.saveWithLines(
        OrderNetstone("", mto3).apply { orderDate = mto3.orderDate.plusDays(1) },
        listOf(netstoneOrderLine(steelFrameZ, "2")),
      )
    logger.info("[Seeder] Created order Netstone SENT: ${nsOrder3.orderNumber}")

    // Delivery note for nsOrder2 (IN_TRANSIT)
    deliveryNoteNetstoneRepository.save(
      DeliveryNoteNetstone(
          numberSequenceService.nextNumber("DELIVERY_NETSTONE", "BL_NST-", 6),
          nsOrder2,
        )
        .apply {
          status = DeliveryNoteNetstone.DeliveryNoteNetstoneStatus.IN_TRANSIT
          shippingDate = mto2.orderDate.plusDays(5)
          containerNumber = "MSCU9876543"
          lot = "LOT-2024-002"
          seals = "SEAL-44512"
        }
    )
    logger.info("[Seeder] Created delivery note Netstone IN_TRANSIT for ${nsOrder2.orderNumber}")

    // Standalone supplier invoice (no linked order) from ChinaSource
    invoiceNetstoneRepository.save(
      InvoiceNetstone(
          numberSequenceService.nextNumber("INVOICE_NETSTONE", "FRN-", 6),
          InvoiceNetstone.RecipientType.PRODUCER,
          chinaSource,
          LocalDate.now().minusDays(30),
        )
        .apply {
          supplierInvoiceNumber = "CS-INV-2024-5521"
          origin = InvoiceNetstone.Origin.STANDALONE
          status = InvoiceNetstone.InvoiceNetstoneStatus.VERIFIED
          dueDate = LocalDate.now().minusDays(30).plusDays(60)
          notes = "Acompte 30% selon accord cadre"
        }
    )
    logger.info("[Seeder] Created standalone invoice Netstone from ChinaSource")

    // Disputed invoice from GlobalParts
    invoiceNetstoneRepository.save(
      InvoiceNetstone(
          numberSequenceService.nextNumber("INVOICE_NETSTONE", "FRN-", 6),
          InvoiceNetstone.RecipientType.PRODUCER,
          globalParts,
          LocalDate.now().minusDays(15),
        )
        .apply {
          supplierInvoiceNumber = "GP-2024-0789"
          origin = InvoiceNetstone.Origin.STANDALONE
          status = InvoiceNetstone.InvoiceNetstoneStatus.DISPUTED
          disputeReason = "Quantité facturée ne correspond pas au bon de livraison (10 vs 8 pcs)."
          amountDiscrepancy = BigDecimal("360.00")
        }
    )
    logger.info("[Seeder] Created disputed invoice Netstone from GlobalParts")

    logger.info("[Seeder] Orders Netstone seeding complete")
  }

  private fun seedSalesCodig(
    clients: List<Client>,
    products: List<Product>,
    refData: ReferenceData,
  ) {
    logger.info("[Seeder] Creating sales Codig...")

    val acme = clients[0]
    val dupont = clients[1]
    val jeanDupont = clients[3]
    val techPro = clients[4]
    val meridian = clients[5]
    val innovatech = clients[7]
    val benali = clients[8]
    val boutique = clients[10]

    val widgetA = products[0]
    val widgetB = products[1]
    val customPartX = products[2]
    val consulting = products[3]
    val installation = products[4]
    val electronicModuleY = products[5]
    val steelFrameZ = products[6]
    val maintenanceKit = products[7]
    val training = products[8]
    val techSupport = products[9]
    val cable10m = products[10]
    val customAssembly = products[11]
    val packagingSet = products[12]
    val sensorModule = products[13]

    // ── DRAFT sales ───────────────────────────────────────────────────────────
    salesCodigService
      .saveWithLines(
        SalesCodig("", acme, LocalDate.now().minusDays(3)).apply {
          subject = "Devis widgets – offre commerciale"
          paymentTerm = refData.paymentTerms[0]
          incoterms = "DAP"
          incotermLocation = "Paris"
        },
        listOf(salesLine(widgetA, acme, "20", "5.00"), salesLine(widgetB, acme, "10")),
      )
      .also { logger.info("[Seeder] Created sale DRAFT: ${it.saleNumber}") }

    salesCodigService
      .saveWithLines(
        SalesCodig("", techPro, LocalDate.now().minusDays(1)).apply {
          subject = "Offre modules électroniques Q2"
          paymentTerm = refData.paymentTerms[0]
          incoterms = "DAP"
          incotermLocation = "Grenoble"
        },
        listOf(
          salesLine(electronicModuleY, techPro, "30"),
          salesLine(sensorModule, techPro, "15"),
          salesLine(techSupport, techPro, "10"),
        ),
      )
      .also { logger.info("[Seeder] Created sale DRAFT: ${it.saleNumber}") }

    salesCodigService
      .saveWithLines(
        SalesCodig("", meridian, LocalDate.now()).apply {
          subject = "UK widget bundle offer"
          paymentTerm = refData.paymentTerms[4]
          currency = "GBP"
          incoterms = "DAP"
          incotermLocation = "London"
        },
        listOf(
          salesLine(widgetA, meridian, "40", "3.00"),
          salesLine(widgetB, meridian, "20", "3.00"),
          salesLine(packagingSet, meridian, "60"),
        ),
      )
      .also { logger.info("[Seeder] Created sale DRAFT: ${it.saleNumber}") }

    salesCodigService
      .saveWithLines(
        SalesCodig("", boutique, LocalDate.now().minusDays(2)).apply {
          subject = "Réassort Widget A – saison"
          paymentTerm = refData.paymentTerms[3]
        },
        listOf(salesLine(widgetA, boutique, "30", "5.00")),
      )
      .also { logger.info("[Seeder] Created sale DRAFT: ${it.saleNumber}") }

    // ── VALIDATED sales (non-MTO — no auto-generated order) ──────────────────
    salesCodigService
      .saveWithLines(
        SalesCodig("", dupont, LocalDate.now().minusDays(10)).apply {
          subject = "Offre consulting + kits"
          status = SalesStatus.VALIDATED
          paymentTerm = refData.paymentTerms[1]
          expectedDeliveryDate = LocalDate.now().plusDays(14)
        },
        listOf(salesLine(consulting, dupont, "20"), salesLine(maintenanceKit, dupont, "8")),
      )
      .also { logger.info("[Seeder] Created sale VALIDATED (non-MTO): ${it.saleNumber}") }

    salesCodigService
      .saveWithLines(
        SalesCodig("", innovatech, LocalDate.now().minusDays(7)).apply {
          subject = "Formation technique + support"
          status = SalesStatus.VALIDATED
          paymentTerm = refData.paymentTerms[1]
          expectedDeliveryDate = LocalDate.now().plusDays(7)
        },
        listOf(
          salesLine(training, innovatech, "3"),
          salesLine(techSupport, innovatech, "20"),
          salesLine(cable10m, innovatech, "50"),
        ),
      )
      .also { logger.info("[Seeder] Created sale VALIDATED (non-MTO): ${it.saleNumber}") }

    salesCodigService
      .saveWithLines(
        SalesCodig("", benali, LocalDate.now().minusDays(4)).apply {
          subject = "Capteurs + installation"
          status = SalesStatus.VALIDATED
          paymentTerm = refData.paymentTerms[3]
          expectedDeliveryDate = LocalDate.now().plusDays(10)
        },
        listOf(salesLine(sensorModule, benali, "2"), salesLine(installation, benali, "4")),
      )
      .also { logger.info("[Seeder] Created sale VALIDATED (non-MTO): ${it.saleNumber}") }

    // ── VALIDATED sales with MTO products (auto-generates OrderCodig chain) ──
    salesCodigService
      .saveWithLines(
        SalesCodig("", jeanDupont, LocalDate.now().minusDays(5)).apply {
          subject = "Pièce custom X"
          status = SalesStatus.VALIDATED
          expectedDeliveryDate = LocalDate.now().plusDays(12)
        },
        listOf(salesLine(customPartX, jeanDupont, "1")),
      )
      .also {
        logger.info("[Seeder] Created sale VALIDATED (MTO, Custom Part X): ${it.saleNumber}")
      }

    salesCodigService
      .saveWithLines(
        SalesCodig("", acme, LocalDate.now().minusDays(8)).apply {
          subject = "Châssis acier + widgets"
          status = SalesStatus.VALIDATED
          paymentTerm = refData.paymentTerms[0]
          expectedDeliveryDate = LocalDate.now().plusDays(20)
          incoterms = "DAP"
          incotermLocation = "Paris"
        },
        listOf(salesLine(steelFrameZ, acme, "4"), salesLine(widgetA, acme, "10")),
      )
      .also {
        logger.info("[Seeder] Created sale VALIDATED (MTO, Steel Frame Z): ${it.saleNumber}")
      }

    salesCodigService
      .saveWithLines(
        SalesCodig("", techPro, LocalDate.now().minusDays(14)).apply {
          subject = "Assemblage custom A – Projet Beta"
          status = SalesStatus.VALIDATED
          paymentTerm = refData.paymentTerms[0]
          expectedDeliveryDate = LocalDate.now().plusDays(25)
          incoterms = "DAP"
          incotermLocation = "Grenoble"
        },
        listOf(salesLine(customAssembly, techPro, "2"), salesLine(techSupport, techPro, "16")),
      )
      .also {
        logger.info("[Seeder] Created sale VALIDATED (MTO, Custom Assembly A): ${it.saleNumber}")
      }

    // ── CANCELLED sale ────────────────────────────────────────────────────────
    salesCodigService
      .saveWithLines(
        SalesCodig("", meridian, LocalDate.now().minusDays(30)).apply {
          subject = "Offre annulée – client a choisi un concurrent"
          status = SalesStatus.CANCELLED
          paymentTerm = refData.paymentTerms[4]
        },
        listOf(salesLine(widgetA, meridian, "100", "10.00")),
      )
      .also { logger.info("[Seeder] Created sale CANCELLED: ${it.saleNumber}") }

    logger.info("[Seeder] Sales Codig seeding complete")
  }
}
