# AI TOOL GUIDANCE

This file provides guidance when working with code in this repository.

## Technology Stack

This is a Vaadin application built with:
- Java (with Lombok)
- Spring Boot
- Spring Data JPA with H2 database
- Maven build system

## Coding Conventions

- **All variables and fields must be `final` whenever possible.** Only omit `final` when the variable genuinely needs to be reassigned.
- **All method and constructor arguments must be `final`.**
- Entities use Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`) with protected ID setters.
- Constructor injection throughout (no `@Autowired` on fields).

## Development Commands

### Running the Application
```bash
./mvnw                           # Start in development mode (default goal: spring-boot:run)
./mvnw spring-boot:run           # Explicit development mode
```

The application will be available at http://localhost:8080

### Building for Production
```bash
./mvnw -Pproduction package      # Build production JAR
docker build -t my-application:latest .  # Build Docker image
```

### Testing
```bash
./mvnw test                      # Run all tests
./mvnw test -Dtest=OrderAServiceTest  # Run a single test class
./mvnw test -Dtest=OrderAServiceTest#someTestMethod  # Run a single test method
```

## Architecture

This project follows a **feature-based package structure** rather than traditional layered architecture. Code is organized by functional units (features), not by technical layers.

### Package Structure

- **`fr.axl.lvy.base`**: Reusable components and base classes
  - `base.ui.MainLayout`: AppLayout with drawer navigation using SideNav, auto-populated from `@Menu` annotations
  - `base.ui.ViewToolbar`: Reusable toolbar component for views

- **`fr.axl.lvy.client`**: Client/supplier management
  - `Client.java`: Entity (COMPANY/INDIVIDUAL, roles: CLIENT/PRODUCER/BOTH, multi-company visibility)
  - `client.contact.Contact.java`: Contact person entity (roles: PRIMARY/BILLING/TECHNICAL/OTHER)
  - `ClientRepository.java`, `ContactRepository.java`, `ClientService.java`
  - `ui.ClientListView.java`

- **`fr.axl.lvy.product`**: Product catalog
  - `Product.java`: Entity (types: PRODUCT/SERVICE, MTO flag, selling/purchase prices)
  - `ProductRepository.java`, `ProductService.java`
  - `ui.ProductListView.java`

- **`fr.axl.lvy.quote`**: Quotations/estimates
  - `Quote.java`: Entity with status workflow (DRAFT → SENT → ACCEPTED/REFUSED/EXPIRED)
  - `QuoteRepository.java`, `QuoteService.java` (includes `convertToOrderA()`)
  - `ui.QuoteListView.java`

- **`fr.axl.lvy.order`**: Order management
  - `OrderA.java`: Internal/retail order entity (CONFIRMED → IN_PRODUCTION → READY → DELIVERED → INVOICED)
  - `OrderB.java`: Supplier/MTO order entity (SENT → CONFIRMED → IN_PRODUCTION → RECEIVED)
  - Repositories, services, and UI views for both

- **`fr.axl.lvy.documentline`**: Reusable line items shared across documents
  - `DocumentLine.java`: Entity linked to documents via type (QUOTE/ORDER_A/ORDER_B/INVOICE_A/INVOICE_B) + documentId
  - `DocumentLineRepository.java`

- **`fr.axl.lvy.delivery`**: Delivery notes
  - `DeliveryNoteA.java`, `DeliveryNoteB.java`: Entities for customer and supplier deliveries

- **`fr.axl.lvy.invoice`**: Invoices
  - `InvoiceA.java`, `InvoiceB.java`: Entities for customer and supplier invoices

- **`fr.axl.lvy.user`**: System users
  - `User.java`: Entity (roles: ADMIN/COLLABORATOR/ACCOUNTANT, multi-company: A/B/AB)

- **`Application.java`**: Main entry point, annotated with `@SpringBootApplication` and `@Theme("default")`

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

- **Server-side rendering**: UI components are Java classes extending Vaadin components
- **Grid lazy loading**: Use `VaadinSpringDataHelpers.toSpringPageRequest(query)` for pagination
- **Themes**: Located in `src/main/frontend/themes/default/`, based on Lumo theme
- **Routing**: `@Route("")` for root path, `@Route("path")` for specific paths
- **Menu**: `@Menu` annotation controls navigation items (order, icon, title)

## Database

- H2 in-memory database for development
- JPA entities use `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Entity equality based on ID (see equals/hashCode pattern in entities)
