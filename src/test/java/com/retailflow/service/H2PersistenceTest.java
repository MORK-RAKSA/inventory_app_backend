package com.retailflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2PersistenceTest {
  @TempDir Path directory;

  @Test
  void migrationsAndStoredDataSurviveDatabaseReopen() throws Exception {
    String url =
        "jdbc:h2:file:"
            + directory.resolve("store")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    Flyway flyway =
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/h2")
            .schemas("public")
            .load();
    flyway.migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE store_settings SET usd_to_khr=4200, currency_mode='live' WHERE id=1");
      statement.execute("SHUTDOWN");
    }
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT usd_to_khr, currency_mode FROM store_settings WHERE id=1")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBigDecimal(1)).isEqualByComparingTo("4200");
      assertThat(result.getString(2)).isEqualTo("live");
    }
  }
}
