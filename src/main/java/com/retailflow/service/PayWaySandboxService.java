package com.retailflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Sandbox-only diagnostics. Never creates sales or changes inventory. */
@Service
@RequiredArgsConstructor
public class PayWaySandboxService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private static final String BASE =
      "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/";

  @Value("${payway.sandbox.enabled:false}")
  private boolean enabled;

  @Value("${payway.sandbox.merchant-id:}")
  private String merchant;

  @Value("${payway.sandbox.api-key:}")
  private String key;

  public record Payment(
      String id,
      String status,
      BigDecimal amount,
      String currency,
      String qrImage,
      long expiresAt,
      boolean sandbox) {}

  public boolean available() {
    return enabled && !merchant.isBlank() && !key.isBlank();
  }

  private void requireEnabled() {
    if (!available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "PayWay sandbox is not configured");
  }

  public Payment create(String requestId, BigDecimal amount) {
    requireEnabled();
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    var existing =
        db.queryForList("SELECT id, amount FROM sandbox_payments WHERE request_id=?", requestId);
    if (!existing.isEmpty()) {
      if (((BigDecimal) existing.getFirst().get("amount")).compareTo(amount) != 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Request ID already used for a different amount");
      return get(existing.getFirst().get("id").toString());
    }
    String id = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    long expires = Instant.now().plusSeconds(360).getEpochSecond();
    try {
      db.update(
          "INSERT INTO sandbox_payments(id,request_id,merchant_id,amount,status,expires_at) VALUES (?,?,?,?,?,?)",
          id,
          requestId,
          merchant,
          amount,
          "CREATING",
          expires);
    } catch (DuplicateKeyException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Payment creation already in progress");
    }
    var fields = common(id);
    fields.put("amount", amount.toPlainString());
    fields.put("items", "");
    fields.put("first_name", "");
    fields.put("last_name", "");
    fields.put("email", "");
    fields.put("phone", "");
    fields.put("purchase_type", "purchase");
    fields.put("payment_option", "abapay_khqr");
    fields.put("callback_url", "");
    fields.put("return_deeplink", "");
    fields.put("currency", "USD");
    fields.put("custom_fields", "");
    fields.put("return_params", "");
    fields.put("payout", "");
    fields.put("lifetime", "6");
    fields.put("qr_image_template", "template1_color");
    try {
      JsonNode response = call("generate-qr", fields);
      if (!"0".equals(response.path("status").path("code").asText()))
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "PayWay rejected QR creation (code "
                + response.path("status").path("code").asText("unknown")
                + "). Check merchant QR access and IP whitelist.");
      String qr = response.path("qrImage").asText();
      if (!qr.startsWith("data:image/png;base64,") || qr.length() > 2_000_000)
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "PayWay returned an unsupported QR image");
      db.update("UPDATE sandbox_payments SET status='PENDING',qr_image=? WHERE id=?", qr, id);
      return get(id);
    } catch (RuntimeException e) {
      db.update("UPDATE sandbox_payments SET status='ERROR' WHERE id=?", id);
      throw e;
    }
  }

  public Payment get(String id) {
    return db
        .query(
            "SELECT * FROM sandbox_payments WHERE id=?",
            (r, n) ->
                new Payment(
                    r.getString("id"),
                    r.getString("status"),
                    r.getBigDecimal("amount"),
                    "USD",
                    r.getString("qr_image"),
                    r.getLong("expires_at"),
                    true),
            id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test payment not found"));
  }

  public Payment check(String id) {
    requireEnabled();
    Payment payment = get(id);
    if (payment.status().equals("APPROVED")) return payment;
    String storedMerchant =
        db.queryForObject("SELECT merchant_id FROM sandbox_payments WHERE id=?", String.class, id);
    if (!merchant.equals(storedMerchant))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant configuration changed");
    JsonNode response = call("check-transaction-2", common(id));
    if ("00".equals(response.path("status").path("code").asText())) {
      if (!id.equals(response.path("status").path("tran_id").asText()))
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment reference mismatch");
      JsonNode data = response.path("data");
      if ("APPROVED".equals(data.path("payment_status").asText())
          && data.path("payment_status_code").asInt(-1) == 0) {
        if (!matchesApprovedAmount(data, payment.amount()))
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY, "Verified payment amount or currency mismatch");
        db.update("UPDATE sandbox_payments SET status='APPROVED' WHERE id=?", id);
      } else if (Instant.now().getEpochSecond() > payment.expiresAt()) {
        db.update(
            "UPDATE sandbox_payments SET status='EXPIRED' WHERE id=? AND status<>'APPROVED'", id);
      }
    } else if (Instant.now().getEpochSecond() > payment.expiresAt()) {
      db.update(
          "UPDATE sandbox_payments SET status='EXPIRED' WHERE id=? AND status<>'APPROVED'", id);
    }
    return get(id);
  }

  static boolean matchesApprovedAmount(JsonNode data, BigDecimal expected) {
    try {
      return "USD".equals(data.path("payment_currency").asText())
          && new BigDecimal(data.path("payment_amount").asText()).compareTo(expected) == 0
          && new BigDecimal(data.path("original_amount").asText()).compareTo(expected) == 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private LinkedHashMap<String, String> common(String id) {
    var fields = new LinkedHashMap<String, String>();
    fields.put(
        "req_time",
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .format(ZonedDateTime.now(ZoneId.of("Asia/Phnom_Penh"))));
    fields.put("merchant_id", merchant);
    fields.put("tran_id", id);
    return fields;
  }

  static String sign(String message, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot sign payment request");
    }
  }

  private JsonNode call(String endpoint, LinkedHashMap<String, String> fields) {
    fields.put("hash", sign(String.join("", fields.values()), key));
    try {
      var request =
          HttpRequest.newBuilder(URI.create(BASE + endpoint))
              .timeout(Duration.ofSeconds(25))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "PayWay sandbox HTTP " + response.statusCode());
      return json.readTree(response.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayWay request interrupted");
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot reach PayWay sandbox");
    }
  }
}
