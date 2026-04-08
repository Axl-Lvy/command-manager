# Command Manager

Order/quote management system built with Vaadin + Spring Boot.

## Tech Stack

- Kotlin, Java 21
- Spring Boot, Vaadin
- Spring Data JPA + MySQL (H2 for tests)
- Gradle

## Getting Started

### Prerequisites

- Java 21+
- MySQL 8+

### Database Setup

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS command_manager;"
```

### Local Credentials

Create `src/main/resources/application-local.properties` (gitignored):

```properties
spring.datasource.password=yourpassword
```

### Run

```bash
./gradlew bootRun
```

The app starts at http://localhost:8080.

### Run Tests

```bash
./gradlew test
```

Tests run against an in-memory H2 database.

## ER Diagram

```mermaid
erDiagram
    %% ═══════════════════════════════════════════
    %% CORE ENTITIES
    %% ═══════════════════════════════════════════

    Client {
        Long id PK
        String clientCode UK
        String name
        ClientType type "COMPANY | INDIVIDUAL"
        ClientRole role "CLIENT | PRODUCER | BOTH"
        Company visibleCompany "A | B | AB"
        String email
        String phone
        String website
        String siret
        String vatNumber
        String billingAddress
        String shippingAddress
        Integer paymentDelay
        String paymentMethod
        BigDecimal defaultDiscount
        Status status "ACTIVE | INACTIVE"
        String notes
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    Contact {
        Long id PK
        String lastName
        String firstName
        String email
        String phone
        String mobile
        String jobTitle
        ContactRole role "PRIMARY | BILLING | TECHNICAL | OTHER"
        boolean active
        Instant createdAt
        Instant updatedAt
    }

    User {
        Long id PK
        String name
        String email UK
        String password
        Role role "ADMIN | COLLABORATOR | ACCOUNTANT"
        Company companyId "A | B | AB"
        boolean active
        Instant lastLogin
        Instant createdAt
        Instant updatedAt
    }

    Product {
        Long id PK
        String reference UK
        String designation
        String description
        ProductType type "PRODUCT | SERVICE"
        boolean mto
        BigDecimal sellingPriceExclTax
        BigDecimal purchasePriceExclTax
        BigDecimal vatRate
        String unit
        String hsCode
        String madeIn
        String clientProductCode
        boolean active
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    %% ═══════════════════════════════════════════
    %% SALES DOCUMENTS (A = customer-facing)
    %% ═══════════════════════════════════════════

    Quote {
        Long id PK
        String quoteNumber UK
        String clientReference
        String subject
        QuoteStatus status "DRAFT | SENT | ACCEPTED | REFUSED | EXPIRED"
        LocalDate quoteDate
        LocalDate validityDate
        String billingAddress
        String shippingAddress
        BigDecimal totalExclTax
        BigDecimal totalVat
        BigDecimal totalInclTax
        String currency
        BigDecimal exchangeRate
        String incoterms
        String notes
        String conditions
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    OrderA {
        Long id PK
        String orderNumber UK
        String clientReference
        String subject
        OrderAStatus status "CONFIRMED | IN_PRODUCTION | READY | DELIVERED | INVOICED | CANCELLED"
        LocalDate orderDate
        LocalDate expectedDeliveryDate
        String billingAddress
        String shippingAddress
        BigDecimal totalExclTax
        BigDecimal totalVat
        BigDecimal totalInclTax
        BigDecimal purchasePriceExclTax
        BigDecimal marginExclTax
        String currency
        BigDecimal exchangeRate
        String incoterms
        String notes
        String conditions
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    DeliveryNoteA {
        Long id PK
        String deliveryNoteNumber UK
        DeliveryNoteAStatus status "PREPARED | SHIPPED | DELIVERED | INCIDENT"
        LocalDate shippingDate
        LocalDate deliveryDate
        String shippingAddress
        String carrier
        String trackingNumber
        Integer packageCount
        String signedBy
        LocalDate signatureDate
        String observations
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    InvoiceA {
        Long id PK
        String invoiceNumber UK
        String clientName
        String clientAddress
        String clientSiret
        String clientVatNumber
        InvoiceAStatus status "DRAFT | ISSUED | OVERDUE | PAID | CANCELLED | CREDIT_NOTE"
        LocalDate invoiceDate
        LocalDate dueDate
        LocalDate paymentDate
        BigDecimal amountPaid
        BigDecimal totalExclTax
        BigDecimal totalVat
        BigDecimal totalInclTax
        String currency
        String incoterms
        String legalNotice
        String latePenalties
        String notes
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    %% ═══════════════════════════════════════════
    %% SUPPLIER DOCUMENTS (B = supplier-facing)
    %% ═══════════════════════════════════════════

    OrderB {
        Long id PK
        String orderNumber UK
        OrderBStatus status "SENT | CONFIRMED | IN_PRODUCTION | RECEIVED | CANCELLED"
        LocalDate orderDate
        LocalDate expectedDeliveryDate
        LocalDate receptionDate
        Boolean receptionConforming
        String receptionReserve
        BigDecimal totalExclTax
        BigDecimal totalVat
        BigDecimal totalInclTax
        String notes
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    DeliveryNoteB {
        Long id PK
        String deliveryNoteNumber UK
        DeliveryNoteBStatus status "IN_TRANSIT | ARRIVED | INSPECTED | INCIDENT"
        LocalDate shippingDate
        LocalDate arrivalDate
        String containerNumber
        String lot
        String seals
        String pdfPath
        String observations
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    InvoiceB {
        Long id PK
        String supplierInvoiceNumber
        String internalInvoiceNumber UK
        RecipientType recipientType "COMPANY_A | PRODUCER"
        Origin origin "ORDER_LINKED | STANDALONE"
        InvoiceBStatus status "RECEIVED | VERIFIED | PAID | DISPUTED"
        LocalDate invoiceDate
        LocalDate dueDate
        LocalDate verificationDate
        LocalDate paymentDate
        String disputeReason
        BigDecimal amountDiscrepancy
        BigDecimal totalExclTax
        BigDecimal totalVat
        BigDecimal totalInclTax
        String pdfPath
        String notes
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }

    %% ═══════════════════════════════════════════
    %% SHARED LINE ITEMS
    %% Polymorphic link via documentType + documentId
    %% (no FK constraint — resolves to Quote,
    %%  OrderA, OrderB, InvoiceA, or InvoiceB)
    %% ═══════════════════════════════════════════

    DocumentLine {
        Long id PK
        DocumentType documentType "QUOTE | ORDER_A | ORDER_B | INVOICE_A | INVOICE_B"
        Long documentId
        String designation
        String description
        String hsCode
        String madeIn
        String clientProductCode
        BigDecimal quantity
        String unit
        BigDecimal unitPriceExclTax
        BigDecimal discountPercent
        BigDecimal vatRate
        BigDecimal vatAmount
        BigDecimal lineTotalExclTax
        int position
        Instant createdAt
        Instant updatedAt
    }

    %% ═══════════════════════════════════════════
    %% RELATIONSHIPS
    %% ═══════════════════════════════════════════

    %% Client contacts
    Client ||--o{ Contact : "has"

    %% Sales flow
    Client ||--o{ Quote : "receives"
    Client ||--o{ OrderA : "places"
    Quote o|--o{ OrderA : "converts to"
    OrderA o|--o{ OrderA : "duplicated from"

    %% Customer delivery and invoicing
    OrderA ||--o| DeliveryNoteA : "ships via"
    Client ||--o{ DeliveryNoteA : "delivered to"
    OrderA o|--o| InvoiceA : "invoiced by"
    DeliveryNoteA o|--o{ InvoiceA : "covers"
    Client ||--o{ InvoiceA : "billed to"
    InvoiceA o|--o{ InvoiceA : "credit note for"

    %% Supplier flow
    OrderA ||--o| OrderB : "triggers"
    OrderB ||--o{ DeliveryNoteB : "received via"
    OrderB o|--o| InvoiceB : "invoiced by"
    Client ||--o{ InvoiceB : "supplier"
    User o|--o{ InvoiceB : "verifies"

    %% Line items
    Product o|--o{ DocumentLine : "referenced in"
```

## Database Schema

Hibernate manages the schema automatically (`ddl-auto=update`). Modify JPA entities and restart.

To reset the schema from scratch (e.g. after renaming tables/columns):

```bash
mysql -u root -p -e "DROP DATABASE command_manager; CREATE DATABASE command_manager;"
```

Then temporarily set `spring.jpa.hibernate.ddl-auto=create` in `application.properties`, start the app, and switch back to `update`.

## Deployment

### Dev instance

A dev instance is automatically deployed to `http://178.104.157.63:8081` on every push to the `dev` branch via GitHub Actions.

## Building for Production

```bash
./gradlew bootJar -Pvaadin.productionMode=true
java -jar build/libs/command-manager-0.1.0.jar
```

Or build a Docker image:

```bash
docker build -t command-manager:latest .
```

Pass database credentials via environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/command_manager \
SPRING_DATASOURCE_USERNAME=app_user \
SPRING_DATASOURCE_PASSWORD=s3cur3pass \
java -jar build/libs/command-manager-0.1.0.jar
```
