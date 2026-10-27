package com.retailflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.retailflow.config.R2StorageClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ImageStorageServiceTest {
  @Test
  void uploadsImageToProductFolderAndReturnsPublicUrl() {
    var storage = mock(R2StorageClient.class);
    var s3 = mock(S3Client.class);
    when(storage.getClient()).thenReturn(s3);
    when(storage.getBucket()).thenReturn("test-bucket");
    when(storage.getPublicUrl()).thenReturn("https://images.example.com");
    var id = UUID.randomUUID();
    var service = new ImageStorageService(storage);
    var result =
        service.upload(
            id,
            new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}));
    assertTrue(result.key().startsWith("products/" + id + "/"));
    assertEquals("https://images.example.com/" + result.key(), result.url());
    verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void rejectsNonImagesWithoutUploading() {
    var storage = mock(R2StorageClient.class);
    var s3 = mock(S3Client.class);
    when(storage.getClient()).thenReturn(s3);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImageStorageService(storage)
                .upload(
                    UUID.randomUUID(),
                    new MockMultipartFile(
                        "file", "fake.png", "image/png", "not an image".getBytes())));
    verifyNoInteractions(s3);
  }
}
