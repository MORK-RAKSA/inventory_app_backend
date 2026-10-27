package com.retailflow.config;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Owns the optional R2 client and closes it when the application stops. */
@Getter
@Component
public class R2StorageClient {
  private final String bucket;
  private final String publicUrl;
  private final S3Client client;

  public R2StorageClient(
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

  @PreDestroy
  public void close() {
    if (client != null) client.close();
  }
}
