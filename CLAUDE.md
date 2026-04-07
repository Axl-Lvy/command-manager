# AI TOOL GUIDANCE

This file provides guidance when working with code in this repository.

## Technology Stack

This is a Vaadin application built with:
- Kotlin, Java 21
- Spring Boot, Vaadin
- Spring Data JPA + MySQL (H2 for tests)
- Gradle build system
- KFmt (Google style), Jacoco, SonarQube

## Coding Conventions

- Entities use Kotlin properties (`var`/`val`) with `private set` for computed/readonly fields.
- Constructor injection throughout (no `@Autowired` on fields).
- **Always format code with KFmt** (`./gradlew ktfmtFormat`) before committing. The project uses Google style. CI will fail on unformatted code.

## Development Commands

### Running the Application
```bash
./gradlew bootRun                # Start in development mode
```

The application will be available at http://localhost:8080

### Formatting
```bash
./gradlew ktfmtFormat                            # Format all Kotlin files (Google style)
```

### Building for Production
```bash
./gradlew bootJar -Pvaadin.productionMode=true   # Build production JAR
docker build -t command-manager:latest .          # Build Docker image
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
  - `BaseEntity.kt`: MappedSuperclass with id, createdAt, updatedAt, equals/hashCode
  - `SoftDeletableEntity.kt`: Extends BaseEntity, adds deletedAt with softDelete()/restore()
  - `ui.MainLayout`: AppLayout with SideNav, auto-populated from `@Menu` annotations
  - `ui.ViewToolbar`: Reusable toolbar component for views

- **`fr.axl.lvy.client`**: Client/supplier management
  - `Client.kt`: Entity (COMPANY/INDIVIDUAL, roles: CLIENT/PRODUCER/BOTH, multi-company visibility)
  - `contact/Contact.kt`: Contact person entity (roles: PRIMARY/BILLING/TECHNICAL/OTHER)
  - `ClientRepository.kt`, `ContactRepository.kt`, `ClientService.kt`
  - `ui/`: ClientListView, ClientFormDialog, ContactFormDialog

- **`fr.axl.lvy.product`**: Product catalog
  - `Product.kt`: Entity (types: PRODUCT/SERVICE, MTO flag, selling/purchase prices)
  - `ProductRepository.kt`, `ProductService.kt`
  - `ui/`: ProductListView, ProductFormDialog

- **`fr.axl.lvy.quote`**: Quotations/estimates
  - `Quote.kt`: Entity with status workflow (DRAFT → SENT → ACCEPTED/REFUSED/EXPIRED)
  - `QuoteRepository.kt`, `QuoteService.kt` (includes `convertToOrderA()`)
  - `ui/`: QuoteListView, QuoteFormDialog

- **`fr.axl.lvy.order`**: Order management
  - `OrderA.kt`: Customer order (CONFIRMED → IN_PRODUCTION → READY → DELIVERED → INVOICED)
  - `OrderB.kt`: Supplier/MTO order (SENT → CONFIRMED → IN_PRODUCTION → RECEIVED)
  - Services with state machines, duplicate(), handleMto()
  - `ui/`: OrderAListView, OrderAFormDialog, OrderBListView, OrderBFormDialog

- **`fr.axl.lvy.documentline`**: Reusable line items shared across documents
  - `DocumentLine.kt`: Linked via DocumentType enum + documentId, with recalculate()
  - `DocumentLineRepository.kt`
  - `ui/DocumentLineEditor.kt`: Inline line item editor component

- **`fr.axl.lvy.delivery`**: Delivery notes
  - `DeliveryNoteA.kt`: Customer delivery (PREPARED → SHIPPED → DELIVERED → INCIDENT)
  - `DeliveryNoteB.kt`: Supplier delivery (IN_TRANSIT → ARRIVED → INSPECTED → INCIDENT)

- **`fr.axl.lvy.invoice`**: Invoices
  - `InvoiceA.kt`: Customer invoice (DRAFT → ISSUED → OVERDUE → PAID → CANCELLED/CREDIT_NOTE)
  - `InvoiceB.kt`: Supplier invoice (RECEIVED → VERIFIED → PAID → DISPUTED)

- **`fr.axl.lvy.user`**: System users
  - `User.kt`: Entity (roles: ADMIN/COLLABORATOR/ACCOUNTANT, multi-company: A/B/AB, canSee() visibility logic)

- **`Application.kt`**: Main entry point, `@SpringBootApplication` and `@Theme("default")`

### Key Architecture Patterns

1. **Feature Packages**: Each feature is self-contained with its own entity, repository, service, UI, and tests
2. **Entity Hierarchy**: BaseEntity (id, audit fields) → SoftDeletableEntity (adds soft delete) → domain entities
3. **Navigation**: Views use `@Route` and `@Menu` annotations. MainLayout builds navigation automatically
4. **Service Layer**: `@Transactional` for writes, `@Transactional(readOnly = true)` for reads
5. **Soft Delete**: Most entities extend SoftDeletableEntity with `softDelete()` and `restore()` methods
6. **Audit Fields**: `createdAt`/`updatedAt` set via `@PrePersist`/`@PreUpdate` in BaseEntity
7. **State Machines**: OrderA, OrderB, and Quote have explicit allowed status transitions
8. **Multi-Company**: User visibility filtering via Company enum (A, B, AB), User.canSee() logic
9. **Document Lines**: Shared polymorphic line items linked via DocumentType + documentId
10. **Dependency Injection**: Constructor injection throughout (no `@Autowired` on fields)

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

- MySQL for development and production (`ddl-auto=update`)
- H2 in-memory for tests (`ddl-auto=create-drop`)
- JPA entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Entity equality based on ID (see equals/hashCode pattern in BaseEntity)
- The project is still in development, so don't care about database migrations for now

## Misc

- Never commit by yourself
