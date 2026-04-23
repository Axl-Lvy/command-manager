package fr.axl.lvy.base

import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.ApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Unit tests for [DocumentLineSchemaUpdater]. Uses mocked [JdbcTemplate] so the MySQL-only branch
 * is reachable from the H2 test runtime.
 */
class DocumentLineSchemaUpdaterTest {

  @Test
  fun run_skips_when_database_is_not_mysql() {
    val jdbc = mockJdbcWithProductName("H2")

    DocumentLineSchemaUpdater(jdbc).run(mock(ApplicationArguments::class.java))

    verify(jdbc, never()).execute(anyString())
    verify(jdbc, never()).queryForList(anyString(), eq(String::class.java))
  }

  @Test
  fun run_skips_when_datasource_metadata_throws() {
    val jdbc = mock(JdbcTemplate::class.java)
    val dataSource = mock(DataSource::class.java)
    `when`(jdbc.dataSource).thenReturn(dataSource)
    `when`(dataSource.connection).thenThrow(RuntimeException("boom"))

    DocumentLineSchemaUpdater(jdbc).run(mock(ApplicationArguments::class.java))

    verify(jdbc, never()).execute(anyString())
  }

  @Test
  fun run_drops_existing_constraint_and_adds_expected_check_for_mysql() {
    val jdbc = mockJdbcWithProductName("MySQL")
    @Suppress("UNCHECKED_CAST")
    `when`(jdbc.queryForList(anyString(), eq(String::class.java)))
      .thenReturn(listOf("document_lines_chk_1"))

    DocumentLineSchemaUpdater(jdbc).run(mock(ApplicationArguments::class.java))

    val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
    verify(jdbc, times(2)).execute(sqlCaptor.capture())
    val executed = sqlCaptor.allValues
    assertThat(executed[0])
      .contains("ALTER TABLE document_lines DROP CHECK")
      .contains("document_lines_chk_1")
    assertThat(executed[1])
      .contains("ADD CONSTRAINT document_lines_document_type_chk")
      .contains("'SALES_CODIG'")
      .contains("'DELIVERY_NETSTONE'")
      .contains("'INVOICE_NETSTONE'")
  }

  @Test
  fun run_adds_constraint_even_when_no_existing_check() {
    val jdbc = mockJdbcWithProductName("MySQL")
    @Suppress("UNCHECKED_CAST")
    `when`(jdbc.queryForList(anyString(), eq(String::class.java))).thenReturn(emptyList())

    DocumentLineSchemaUpdater(jdbc).run(mock(ApplicationArguments::class.java))

    val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
    verify(jdbc, times(1)).execute(sqlCaptor.capture())
    assertThat(sqlCaptor.value).contains("ADD CONSTRAINT document_lines_document_type_chk")
  }

  @Test
  fun run_drops_each_existing_constraint() {
    val jdbc = mockJdbcWithProductName("MySQL")
    @Suppress("UNCHECKED_CAST")
    `when`(jdbc.queryForList(anyString(), eq(String::class.java))).thenReturn(listOf("c1", "c2"))

    DocumentLineSchemaUpdater(jdbc).run(mock(ApplicationArguments::class.java))

    val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
    verify(jdbc, times(3)).execute(sqlCaptor.capture())
    val statements = sqlCaptor.allValues
    assertThat(statements[0]).contains("DROP CHECK `c1`")
    assertThat(statements[1]).contains("DROP CHECK `c2`")
    assertThat(statements[2]).contains("ADD CONSTRAINT")
  }

  private fun mockJdbcWithProductName(productName: String): JdbcTemplate {
    val jdbc = mock(JdbcTemplate::class.java)
    val dataSource = mock(DataSource::class.java)
    val connection = mock(Connection::class.java)
    val metaData = mock(DatabaseMetaData::class.java)
    `when`(jdbc.dataSource).thenReturn(dataSource)
    `when`(dataSource.connection).thenReturn(connection)
    `when`(connection.metaData).thenReturn(metaData)
    `when`(metaData.databaseProductName).thenReturn(productName)
    return jdbc
  }
}
