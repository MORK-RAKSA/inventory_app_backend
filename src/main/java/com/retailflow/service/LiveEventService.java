package com.retailflow.service;

import com.retailflow.sales.Sale;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
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
      Sale sale) {}

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
      String type,
      String titleEn,
      String titleKh,
      String detail,
      String productId,
      Sale sale) {
    long now = System.currentTimeMillis();
    LiveEvent ev =
        new LiveEvent(
            UUID.randomUUID().toString(),
            type,
            titleEn,
            titleKh,
            detail,
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            now,
            currentClientId(),
            productId,
            sale);
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
