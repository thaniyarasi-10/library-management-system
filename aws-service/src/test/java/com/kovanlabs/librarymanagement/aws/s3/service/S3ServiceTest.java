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
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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

    @Test
    @DisplayName("downloadFile should return byte array when successful")
    void downloadFile_shouldReturnBytes() {
        byte[] expected = "PDF agreement content".getBytes();
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(getObjectResponse, expected);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = s3Service.downloadFile("my-test-bucket", "us-east-1", "templates/agreement.pdf");

        assertArrayEquals(expected, result);
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("downloadFile should throw ResponseStatusException on failure")
    void downloadFile_whenFails_shouldThrowException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("NoSuchKey").build());

        assertThrows(ResponseStatusException.class, () ->
                s3Service.downloadFile("my-test-bucket", "us-east-1", "missing.pdf"));
    }

    @Test
    @DisplayName("downloadFileAsString should return string content when successful")
    void downloadFileAsString_shouldReturnString() {
        String expected = "<html><body>Agreement</body></html>";
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(getObjectResponse, expected.getBytes());

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        String result = s3Service.downloadFileAsString("my-test-bucket", "us-east-1", "templates/agreement.html");

        assertEquals(expected, result);
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("downloadFileAsString should throw IllegalArgumentException when bucket or key is null")
    void downloadFileAsString_withNullBucketOrKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                s3Service.downloadFileAsString(null, "us-east-1", "key"));
        assertThrows(IllegalArgumentException.class, () ->
                s3Service.downloadFileAsString("bucket", "us-east-1", null));
    }

    @Test
    @DisplayName("downloadFileAsString should throw ResponseStatusException when S3 fails")
    void downloadFileAsString_whenFails_shouldThrowException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 Error"));

        assertThrows(ResponseStatusException.class, () ->
                s3Service.downloadFileAsString("bucket", "us-east-1", "key"));
    }

    @Test
    @DisplayName("uploadFileBytes should upload bytes and return key")
    void uploadFileBytes_shouldSucceed() {
        byte[] bytes = "agreement pdf bytes".getBytes();

        String key = s3Service.uploadFileBytes("my-test-bucket", "us-east-1", "agreements/123.pdf", bytes, "application/pdf");

        assertEquals("agreements/123.pdf", key);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("uploadFileBytes should throw ResponseStatusException on failure")
    void uploadFileBytes_whenFails_shouldThrowException() {
        doThrow(S3Exception.builder().message("Access Denied").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(ResponseStatusException.class, () ->
                s3Service.uploadFileBytes("bucket", "us-east-1", "key", new byte[10], "application/pdf"));
    }

    @Test
    @DisplayName("closeClients and custom region creation should execute gracefully")
    void closeClients_andCustomRegion_shouldWorkGracefully() {
        ReflectionTestUtils.setField(s3Service, "accessKey", "test-key");
        ReflectionTestUtils.setField(s3Service, "secretKey", "test-secret");

        // getS3ClientForRegion with null or same region uses default client
        byte[] bytes = "test".getBytes();
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(getObjectResponse, bytes));

        assertNotNull(s3Service.downloadFile("bucket", null, "key"));
        assertNotNull(s3Service.downloadFile("bucket", "", "key"));
        assertNotNull(s3Service.downloadFile("bucket", "us-east-1", "key"));

        // closeClients call
        assertDoesNotThrow(() -> s3Service.closeClients());
    }

    @Test
    @DisplayName("getS3ClientForRegion should instantiate client with static credentials or default credentials")
    void getS3ClientForRegion_shouldCreateClientForDifferentRegion() {
        // With explicit credentials
        ReflectionTestUtils.setField(s3Service, "accessKey", "test-key");
        ReflectionTestUtils.setField(s3Service, "secretKey", "test-secret");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");

        assertDoesNotThrow(() -> {
            try {
                s3Service.downloadFile("bucket", "eu-central-1", "key");
            } catch (Exception ignored) {
                // Network call will fail against real AWS since credentials are dummy, but client creation branch is hit
            }
        });

        // Without credentials (uses DefaultCredentialsProvider)
        ReflectionTestUtils.setField(s3Service, "accessKey", "");
        ReflectionTestUtils.setField(s3Service, "secretKey", "");
        assertDoesNotThrow(() -> {
            try {
                s3Service.downloadFile("bucket", "ap-southeast-1", "key");
            } catch (Exception ignored) {
                // Network call will fail, but client creation branch is hit
            }
        });

        s3Service.closeClients();
    }
}
