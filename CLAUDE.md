# AI TOOL GUIDANCE

This file provides guidance when working with code in this repository.

## Technology Stack

This is a Vaadin application built with:
- Kotlin
- Spring Boot
- Spring Data JPA with H2 database
- Gradle build system

## Coding Conventions

- Entities use Kotlin properties (`var`/`val`) with `private set` for computed/readonly fields.
- Constructor injection throughout (no `@Autowired` on fields).

## Development Commands

### Running the Application
```bash
./gradlew bootRun                # Start in development mode
```

The application will be available at http://localhost:8080

### Building for Production
```bash
./gradlew bootJar -Pvaadin.productionMode=true  # Build production JAR
docker build -t my-application:latest .          # Build Docker image
```

### Testing
```bash
./gradlew test                                                        # Run all tests
./gradlew test --tests "fr.axl.lvy.order.OrderAServiceTest"           # Run a single test class
./gradlew test --tests "fr.axl.lvy.order.OrderAServiceTest.someTest"  # Run a single test method
```

## Architecture

This project follows a **feature-based package structure** rather than traditional layered architecture. Code is organized by functional units (features), not by technical layers.

### Package Structure

- **`fr.axl.lvy.base`**: Reusable components and base classes
  - `base.ui.MainLayout`: AppLayout with drawer navigation using SideNav, auto-populated from `@Menu` annotations
  - `base.ui.ViewToolbar`: Reusable toolbar component for views

- **`fr.axl.lvy.client`**: Client/supplier management
  - `Client.kt`: Entity (COMPANY/INDIVIDUAL, roles: CLIENT/PRODUCER/BOTH, multi-company visibility)
  - `client.contact.Contact.kt`: Contact person entity (roles: PRIMARY/BILLING/TECHNICAL/OTHER)
  - `ClientRepository.kt`, `ContactRepository.kt`, `ClientService.kt`
  - `ui.ClientListView.kt`

- **`fr.axl.lvy.product`**: Product catalog
  - `Product.kt`: Entity (types: PRODUCT/SERVICE, MTO flag, selling/purchase prices)
  - `ProductRepository.kt`, `ProductService.kt`
  - `ui.ProductListView.kt`

- **`fr.axl.lvy.quote`**: Quotations/estimates
  - `Quote.kt`: Entity with status workflow (DRAFT → SENT → ACCEPTED/REFUSED/EXPIRED)
  - `QuoteRepository.kt`, `QuoteService.kt` (includes `convertToOrderA()`)
  - `ui.QuoteListView.kt`

- **`fr.axl.lvy.order`**: Order management
  - `OrderA.kt`: Internal/retail order entity (CONFIRMED → IN_PRODUCTION → READY → DELIVERED → INVOICED)
  - `OrderB.kt`: Supplier/MTO order entity (SENT → CONFIRMED → IN_PRODUCTION → RECEIVED)
  - Repositories, services, and UI views for both

- **`fr.axl.lvy.documentline`**: Reusable line items shared across documents
  - `DocumentLine.kt`: Entity linked to documents via type (QUOTE/ORDER_A/ORDER_B/INVOICE_A/INVOICE_B) + documentId
  - `DocumentLineRepository.kt`

- **`fr.axl.lvy.delivery`**: Delivery notes
  - `DeliveryNoteA.kt`, `DeliveryNoteB.kt`: Entities for customer and supplier deliveries

- **`fr.axl.lvy.invoice`**: Invoices
  - `InvoiceA.kt`, `InvoiceB.kt`: Entities for customer and supplier invoices

- **`fr.axl.lvy.user`**: System users
  - `User.kt`: Entity (roles: ADMIN/COLLABORATOR/ACCOUNTANT, multi-company: A/B/AB)

- **`Application.kt`**: Main entry point, annotated with `@SpringBootApplication` and `@Theme("default")`

### Key Architecture Patterns

1. **Feature Packages**: Each feature is self-contained with its own entity, repository, service, UI, and tests
2. **Navigation**: Views use `@Route` and `@Menu` annotations. MainLayout builds navigation automatically
3. **Service Layer**: `@Transactional` for writes, `@Transactional(readOnly = true)` for reads
4. **Soft Delete**: Most entities have `deletedAt` field with `softDelete()` and `restore()` methods
5. **Audit Fields**: Entities have `createdAt`/`updatedAt` set via `@PrePersist`/`@PreUpdate`
6. **State Machines**: OrderA and Quote have explicit allowed status transitions
7. **Multi-Company**: User visibility filtering via Company enum (A, B, AB)
8. **Dependency Injection**: Constructor injection throughout (no `@Autowired` on fields)

## Adding New Features

When creating a new feature:
1. Create a new package under `fr.axl.lvy` (e.g., `fr.axl.lvy.myfeature`)
2. Include: Entity, Repository, Service, and UI view classes
3. Use existing feature packages as a reference

## Vaadin-Specific Notes

- **Server-side rendering**: UI components are Kotlin classes extending Vaadin components
- **Grid lazy loading**: Use `VaadinSpringDataHelpers.toSpringPageRequest(query)` for pagination
- **Themes**: Located in `src/main/frontend/themes/default/`, based on Lumo theme
- **Routing**: `@Route("")` for root path, `@Route("path")` for specific paths
- **Menu**: `@Menu` annotation controls navigation items (order, icon, title)

## Database

- H2 in-memory database for development
- JPA entities use `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Entity equality based on ID (see equals/hashCode pattern in entities)
