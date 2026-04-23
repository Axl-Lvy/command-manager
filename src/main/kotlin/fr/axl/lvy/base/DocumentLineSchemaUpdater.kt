package fr.axl.lvy.base

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Keeps the development MySQL enum/check constraint aligned while the project still runs without
 * migrations. Hibernate update does not reliably expand existing check constraints.
 */
@Component
class DocumentLineSchemaUpdater(private val jdbcTemplate: JdbcTemplate) : ApplicationRunner {

  override fun run(args: ApplicationArguments) {
    if (!isMySql()) return

    val constraints =
      jdbcTemplate.queryForList(
        """
          SELECT tc.CONSTRAINT_NAME
          FROM information_schema.TABLE_CONSTRAINTS tc
          JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
           AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
          WHERE tc.TABLE_SCHEMA = DATABASE()
            AND tc.TABLE_NAME = 'document_lines'
            AND tc.CONSTRAINT_TYPE = 'CHECK'
            AND LOWER(cc.CHECK_CLAUSE) LIKE '%document_type%'
        """,
        String::class.java,
      )

    val expectedValues =
      listOf(
        "SALES_CODIG",
        "SALES_NETSTONE",
        "ORDER_CODIG",
        "ORDER_NETSTONE",
        "DELIVERY_NETSTONE",
        "INVOICE_CODIG",
        "INVOICE_NETSTONE",
      )
    val expectedCheck =
      expectedValues.joinToString(prefix = "document_type IN (", postfix = ")") { "'$it'" }

    constraints.forEach { constraint ->
      jdbcTemplate.execute("ALTER TABLE document_lines DROP CHECK `$constraint`")
    }
    jdbcTemplate.execute(
      "ALTER TABLE document_lines ADD CONSTRAINT document_lines_document_type_chk CHECK ($expectedCheck)"
    )
    log.info("Document line document_type check constraint aligned")
  }

  private fun isMySql(): Boolean =
    runCatching {
        jdbcTemplate.dataSource?.connection?.use { connection ->
          connection.metaData.databaseProductName.contains("MySQL", ignoreCase = true)
        } == true
      }
      .getOrDefault(false)

  companion object {
    private val log = LoggerFactory.getLogger(DocumentLineSchemaUpdater::class.java)
  }
}
