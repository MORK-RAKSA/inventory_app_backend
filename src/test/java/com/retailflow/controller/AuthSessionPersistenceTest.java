package com.retailflow.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.retailflow.entity.AppUser;
import com.retailflow.service.contract.UserService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthSessionPersistenceTest {
  @Test
  void repairsMissingSessionTableOnExistingDatabase() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:authrepair;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    Flyway.configure().dataSource(ds).locations("classpath:db/h2").target("17").load().migrate();
    var db = new JdbcTemplate(ds);
    db.execute("DROP TABLE auth_sessions");
    Flyway.configure().dataSource(ds).locations("classpath:db/h2").load().migrate();
    assertNotNull(db.queryForList("SELECT * FROM auth_sessions"));
    assertEquals(
        1,
        db.queryForObject("SELECT COUNT(*) FROM app_users WHERE username='admin'", Integer.class));
  }

  @Test
  void successfulLoginPersistsAndRefreshesSession() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:authflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    Flyway.configure().dataSource(ds).locations("classpath:db/h2").load().migrate();
    var db = new JdbcTemplate(ds);
    var users = mock(UserService.class);
    var admin = new AppUser();
    admin.id = db.queryForObject("SELECT id FROM app_users WHERE username='admin'", String.class);
    admin.username = "admin";
    admin.fullName = "Admin";
    admin.role = "ADMIN";
    when(users.authenticate("admin", "admin1234")).thenReturn(admin);
    var controller = new AuthController(db, users);
    var session = controller.login(new AuthController.Credentials("admin", "admin1234"));
    assertNotNull(controller.userForAccess("Bearer " + session.accessToken()));
    var refreshed = controller.refresh(new AuthController.RefreshRequest(session.refreshToken()));
    assertNotNull(controller.userForAccess("Bearer " + refreshed.accessToken()));
  }
}
