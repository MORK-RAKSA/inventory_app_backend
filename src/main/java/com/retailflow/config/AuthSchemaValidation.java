package com.retailflow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC session tables are not Hibernate entities and must be checked explicitly. */
@Component
@RequiredArgsConstructor
public class AuthSchemaValidation implements ApplicationRunner {
  private final JdbcTemplate db;

  @Override
  public void run(ApplicationArguments args) {
    try {
      db.queryForList(
          "SELECT access_hash, refresh_hash, access_expires_at, session_expires_at, user_id FROM auth_sessions WHERE 1=0");
    } catch (org.springframework.dao.DataAccessException e) {
      throw new IllegalStateException(
          "Authentication schema is missing. Enable Flyway, use classpath:db/h2 for H2, and ensure DB_SCHEMA matches the datasource schema. Deploy migration V18 before serving traffic.",
          e);
    }
  }
}
