package com.retailflow.service;

import com.retailflow.entity.Sale;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class LiveEventService {

  public record LiveEvent(
      String id,
      String type,
      String titleEn,
      String titleKh,
      String detail,
      String createdAt,
      long timestampMs,
      String clientId,
      String productId,
      Sale sale,
      String barcode,
      String scanMode) {}

  private final Deque<LiveEvent> events = new ConcurrentLinkedDeque<>();

  private static String currentClientId() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest req = attrs.getRequest();
        String cid = req.getHeader("X-Client-Id");
        return cid != null ? cid : "";
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  public void push(
      String type, String titleEn, String titleKh, String detail, String productId, Sale sale) {
    append(
        new LiveEvent(
            UUID.randomUUID().toString(),
            type,
            titleEn,
            titleKh,
            detail,
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            System.currentTimeMillis(),
            currentClientId(),
            productId,
            sale,
            null,
            null));
  }

  /**
   * Wireless mobile scanner gun broadcast. The web portal polls {@code /api/events} and routes
   * itself to the POS / Inventory / Product form / Batch intake screen carried by {@code scanMode}.
   */
  public LiveEvent pushScan(String barcode, String scanMode) {
    String mode = scanMode == null || scanMode.isBlank() ? "checkout" : scanMode.trim();
    LiveEvent ev =
        new LiveEvent(
            UUID.randomUUID().toString(),
            "SCANNER_GUN_SCAN",
            "Mobile Scanner Gun: " + barcode,
            "ស្កេនពីទូរស័ព្ទ៖ " + barcode,
            barcode,
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            System.currentTimeMillis(),
            currentClientId(),
            null,
            null,
            barcode,
            mode);
    append(ev);
    return ev;
  }

  private void append(LiveEvent ev) {
    events.addFirst(ev);
    while (events.size() > 60) {
      events.removeLast();
    }
  }

  public List<LiveEvent> since(long sinceMs) {
    List<LiveEvent> out = new ArrayList<>();
    for (LiveEvent ev : events) {
      if (ev.timestampMs() > sinceMs) {
        out.add(ev);
      }
    }
    return out;
  }
}
