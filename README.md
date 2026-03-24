# Command Manager

Order/quote management system built with Vaadin + Spring Boot.

## Tech Stack

- Java 25, Spring Boot 4, Vaadin 25
- Spring Data JPA + MySQL
- Lombok
- Flyway (for production migrations)
- Maven

## Getting Started

### Prerequisites

- Java 25
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
./mvnw
```

The app starts at http://localhost:8080.

## Database Migrations

### During Development

Hibernate manages the schema automatically (`ddl-auto=update`). No migration files needed — just modify your JPA entities and restart.

### Preparing for Production

Before the first production deployment:

1. Export the current schema:
   ```bash
   mysqldump --no-data command_manager > src/main/resources/db/migration/V1__initial_schema.sql
   ```
2. In `application.properties`, switch:
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   ```

### After Production Deployment

For every schema change, write a new Flyway migration file:

```
src/main/resources/db/migration/V2__add_column_x.sql
src/main/resources/db/migration/V3__create_table_y.sql
```

Flyway runs them automatically on startup. Never use `ddl-auto=update` in production.

## Building for Production

```bash
./mvnw -Pproduction package
java -jar target/command-manager-1.0-SNAPSHOT.jar
```

Pass database credentials via environment variables:

```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/command_manager \
SPRING_DATASOURCE_USERNAME=app_user \
SPRING_DATASOURCE_PASSWORD=s3cur3pass \
java -jar target/command-manager-1.0-SNAPSHOT.jar
```
