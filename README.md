# Command Manager

Order/quote management system built with Vaadin + Spring Boot.

## Tech Stack

- Java 25, Spring Boot 4, Vaadin 25
- Spring Data JPA + MySQL
- Lombok
- Maven

## Getting Started

### Prerequisites

- Java 25+
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

### Run Tests

```bash
./mvnw test
```

Tests run against the same MySQL database. Make sure it exists before running.

## Database Schema

Hibernate manages the schema automatically (`ddl-auto=update`). Just modify JPA entities and restart.

To reset the schema from scratch (e.g. after renaming tables/columns):

```bash
mysql -u root -p -e "DROP DATABASE command_manager; CREATE DATABASE command_manager;"
```

Then temporarily set `spring.jpa.hibernate.ddl-auto=create` in `application.properties`, start the app, and switch back to `update`.

## Building for Production

```bash
./mvnw -Pproduction package
java -jar target/command-manager-1.0-SNAPSHOT.jar
```

Pass database credentials via environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/command_manager \
SPRING_DATASOURCE_USERNAME=app_user \
SPRING_DATASOURCE_PASSWORD=s3cur3pass \
java -jar target/command-manager-1.0-SNAPSHOT.jar
```
