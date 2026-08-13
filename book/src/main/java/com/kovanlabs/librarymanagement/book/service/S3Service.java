package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.S3UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region:us-east-1}")
    private String region;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public S3UploadResponse uploadFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "cover.jpg";
        String key = "book-cover-" + UUID.randomUUID() + "-" + originalFilename;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            String url = s3Client.utilities()
                    .getUrl(builder -> builder.bucket(bucketName).key(key))
                    .toString();

            return new S3UploadResponse(key, url);
        } catch (S3Exception e) {
            log.error("Error uploading file to S3 bucket {}: {}", bucketName, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    public void deleteFile(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file with key {} from S3 bucket {}", key, bucketName);
        } catch (S3Exception e) {
            log.error("Error deleting file with key {} from S3: {}", key, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to delete file from S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }
}
