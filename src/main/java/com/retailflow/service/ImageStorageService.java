package com.retailflow.service;

import com.retailflow.config.R2StorageClient;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Optional product-image storage on Cloudflare R2 (S3 compatible). Disabled unless fully
 * configured.
 */
@Service
@RequiredArgsConstructor
public class ImageStorageService {
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  public record Stored(String key, String url) {}

  private final R2StorageClient storage;

  public boolean enabled() {
    return storage.getClient() != null;
  }

  public Stored upload(UUID productId, MultipartFile file) {
    if (!enabled())
      throw new ResponseStatusException(
          HttpStatus.NOT_IMPLEMENTED, "Image storage is not configured on the server");
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("Image file is empty");
    if (file.getSize() > MAX_BYTES)
      throw new IllegalArgumentException("Image must be 5 MB or smaller");
    try {
      byte[] head = file.getInputStream().readNBytes(12);
      String[] type = sniff(head);
      if (type == null)
        throw new IllegalArgumentException("Only JPEG, PNG or WebP images are allowed");
      String key = "products/" + productId + "/" + UUID.randomUUID() + "." + type[1];
      storage
          .getClient()
          .putObject(
              PutObjectRequest.builder()
                  .bucket(storage.getBucket())
                  .key(key)
                  .contentType(type[0])
                  .cacheControl("public, max-age=31536000, immutable")
                  .build(),
              RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      return new Stored(key, storage.getPublicUrl() + "/" + key);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not read or store the image", e);
    }
  }

  /** Best-effort delete; a leftover object must never fail the API call. */
  public void delete(String key) {
    if (!enabled() || key == null || key.isBlank()) return;
    try {
      storage
          .getClient()
          .deleteObject(DeleteObjectRequest.builder().bucket(storage.getBucket()).key(key).build());
    } catch (RuntimeException ignored) {
      // orphaned objects are harmless
    }
  }

  /** Detects the real format from magic bytes instead of trusting the client's Content-Type. */
  private static String[] sniff(byte[] b) {
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
      return new String[] {"image/jpeg", "jpg"};
    if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
      return new String[] {"image/png", "png"};
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') return new String[] {"image/webp", "webp"};
    return null;
  }
}
