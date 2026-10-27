package com.retailflow.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PayWaySandboxServiceTest {
  @Test
  void signsRfc4231Vector() {
    String expected =
        "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"
            + "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854";
    assertEquals(
        Base64.getEncoder().encodeToString(HexFormat.of().parseHex(expected)),
        PayWaySandboxService.sign("Hi There", "\u000b".repeat(20)));
  }

  @Test
  void rejectsWrongCurrencyMissingAndMismatchedAmounts() throws Exception {
    var mapper = new ObjectMapper();
    var data =
        mapper.readTree(
            "{\"payment_currency\":\"USD\",\"payment_amount\":0.01,\"original_amount\":0.01}");
    assertTrue(PayWaySandboxService.matchesApprovedAmount(data, new BigDecimal("0.01")));
    assertFalse(PayWaySandboxService.matchesApprovedAmount(data, new BigDecimal("1")));
    assertFalse(
        PayWaySandboxService.matchesApprovedAmount(mapper.readTree("{}"), new BigDecimal("0.01")));
    ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("payment_currency", "KHR");
    assertFalse(PayWaySandboxService.matchesApprovedAmount(data, new BigDecimal("0.01")));
  }
}
