package com.retailflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class DevicePushService {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  @TransactionalEventListener(fallbackExecution = true)
  public void onActivity(LiveEventService.LiveEvent event) {
    if (!java.util.Set.of("CHECKOUT", "PRODUCT_ADDED", "PRODUCT_UPDATED").contains(event.type()))
      return;
    CompletableFuture.runAsync(
        () -> {
          try {
            var tokens =
                db.queryForList(
                    "SELECT t.token FROM device_push_tokens t JOIN app_users u ON u.id=t.user_id WHERE u.active=true AND t.client_id<>?",
                    String.class,
                    event.clientId());
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            for (String token : tokens) {
              var payload =
                  Map.of(
                      "to",
                      token,
                      "title",
                      event.titleKh(),
                      "body",
                      event.detail(),
                      "sound",
                      "default",
                      "channelId",
                      "store-activity",
                      "data",
                      Map.of("eventId", event.id()));
              var response =
                  http.send(
                      HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/send"))
                          .timeout(Duration.ofSeconds(10))
                          .header("Content-Type", "application/json")
                          .POST(
                              HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                          .build(),
                      HttpResponse.BodyHandlers.ofString());
              if ("DeviceNotRegistered"
                  .equals(
                      json.readTree(response.body())
                          .path("data")
                          .path("details")
                          .path("error")
                          .asText()))
                db.update("DELETE FROM device_push_tokens WHERE token=?", token);
            }
          } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DevicePushService.class)
                .warn("Device notification delivery unavailable");
          }
        });
  }
}
