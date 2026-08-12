package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.S3UploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Utilities s3Utilities;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client);
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");
    }

    @Test
    @DisplayName("uploadFile with valid file should put object and return S3UploadResponse")
    void uploadFile_WithValidFile_ShouldReturnUploadResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "image content".getBytes());
        URL expectedUrl = URI.create("https://test-bucket.s3.us-east-1.amazonaws.com/book-cover-key").toURL();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.getUrl(any(Consumer.class))).thenReturn(expectedUrl);

        S3UploadResponse response = s3Service.uploadFile(file);

        assertNotNull(response);
        assertNotNull(response.coverImageKey());
        assertTrue(response.coverImageKey().startsWith("book-cover-"));
        assertEquals(expectedUrl.toString(), response.coverImageUrl());

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("uploadFile with empty file should throw IllegalArgumentException")
    void uploadFile_WithEmptyFile_ShouldThrowIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> s3Service.uploadFile(emptyFile));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("deleteFile with valid key should invoke s3Client deleteObject")
    void deleteFile_WithValidKey_ShouldInvokeDeleteObject() {
        String key = "book-cover-123.jpg";

        assertDoesNotThrow(() -> s3Service.deleteFile(key));

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("deleteFile with blank or null key should do nothing")
    void deleteFile_WithNullOrBlankKey_ShouldDoNothing() {
        s3Service.deleteFile(null);
        s3Service.deleteFile("   ");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
