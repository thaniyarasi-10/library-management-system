package com.kovanlabs.librarymanagement.aws.s3.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client);
        ReflectionTestUtils.setField(s3Service, "bucketName", "my-test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");
    }

    @Test
    @DisplayName("Upload file successfully to S3 and return key and URL")
    void uploadFile_Successfully() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.jpg",
                "image/jpeg",
                "dummy image content".getBytes()
        );

        S3UploadResponse response = s3Service.uploadFile(file);

        assertNotNull(response);
        assertNotNull(response.coverImageKey());
        assertTrue(response.coverImageKey().contains("cover.jpg"));
        assertNotNull(response.coverImageUrl());
        assertTrue(response.coverImageUrl().startsWith("https://my-test-bucket.s3.us-east-1.amazonaws.com/"));

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("S3Client throws S3Exception during upload")
    void uploadFile_S3Exception_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.jpg",
                "image/jpeg",
                "dummy image content".getBytes()
        );

        doThrow(S3Exception.builder().message("Access Denied to S3 bucket").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(S3Exception.class, () -> s3Service.uploadFile(file));
    }

    @Test
    @DisplayName("Empty file upload handles generation")
    void uploadFile_EmptyFile_SucceedsWithKey() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]
        );

        S3UploadResponse response = s3Service.uploadFile(emptyFile);

        assertNotNull(response);
        assertTrue(response.coverImageKey().contains("empty.png"));
    }
}
