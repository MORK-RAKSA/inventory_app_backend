package com.retailflow.service;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Optional product-image storage on Cloudflare R2 (S3 compatible). Disabled unless fully
 * configured.
 */
@Service
public class ImageStorageService {
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  public record Stored(String key, String url) {}

  private final String bucket;
  private final String publicUrl;
  private final S3Client client;

  public ImageStorageService(
      @Value("${retailflow.storage.r2.account-id:}") String accountId,
      @Value("${retailflow.storage.r2.access-key-id:}") String accessKey,
      @Value("${retailflow.storage.r2.secret-access-key:}") String secretKey,
      @Value("${retailflow.storage.r2.bucket:}") String bucket,
      @Value("${retailflow.storage.r2.public-url:}") String publicUrl) {
    boolean configured =
        !accountId.isBlank()
            && !accessKey.isBlank()
            && !secretKey.isBlank()
            && !bucket.isBlank()
            && !publicUrl.isBlank();
    this.bucket = bucket;
    this.publicUrl = publicUrl.replaceAll("/+$", "");
    this.client =
        !configured
            ? null
            : S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                    S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
  }

  public boolean enabled() {
    return client != null;
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
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(type[0])
              .cacheControl("public, max-age=31536000, immutable")
              .build(),
          RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      return new Stored(key, publicUrl + "/" + key);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not read or store the image", e);
    }
  }

  /** Best-effort delete; a leftover object must never fail the API call. */
  public void delete(String key) {
    if (!enabled() || key == null || key.isBlank()) return;
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
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
