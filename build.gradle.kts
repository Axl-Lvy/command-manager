plugins {
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
  kotlin("jvm") version "2.1.0"
  kotlin("plugin.spring") version "2.1.0"
  kotlin("plugin.jpa") version "2.1.0"
  id("com.vaadin") version "25.0.7"
  id("com.ncorti.ktfmt.gradle") version "0.22.0"
}

group = "fr.axl.lvy"

version = "1.0-SNAPSHOT"

kotlin { jvmToolchain(25) }

repositories {
  mavenCentral()
  maven { url = uri("https://maven.vaadin.com/vaadin-addons") }
}

dependencyManagement { imports { mavenBom("com.vaadin:vaadin-bom:25.0.7") } }

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))
  implementation("com.vaadin:vaadin")
  developmentOnly("com.vaadin:vaadin-dev")
  implementation("com.vaadin:vaadin-spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  runtimeOnly("com.mysql:mysql-connector-j")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("com.h2database:h2")
}

ktfmt { googleStyle() }
