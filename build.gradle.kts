plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.vaadin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.sonarqube)
  jacoco
}

group = "fr.axl.lvy"

version = "0.1.0"

kotlin { jvmToolchain(21) }

repositories {
  mavenCentral()
  maven { url = uri("https://maven.vaadin.com/vaadin-addons") }
}

dependencyManagement { imports { mavenBom(libs.vaadin.bom.get().toString()) } }

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))
  implementation(libs.vaadin)
  developmentOnly(libs.vaadin.dev)
  implementation(libs.vaadin.spring.boot.starter)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.validation)
  runtimeOnly(libs.mysql.connector)
  testImplementation(libs.spring.boot.starter.test)
  testRuntimeOnly(libs.h2)
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
  testLogging { events("passed", "skipped", "failed") }
}

tasks.jacocoTestReport { reports { xml.required = true } }

ktfmt { googleStyle() }

sonar {
  properties {
    property("sonar.projectKey", "Axl-Lvy_command-manager")
    property("sonar.organization", "axl-lvy")
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.exclusions", "**/ui/**")
  }
}
