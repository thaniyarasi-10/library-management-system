package com.kovanlabs.librarymanagement.aws.s3.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class S3Service {
    private S3Client s3Client;

    @Value("${aws.s3.book-covers.bucket-name:}")
    private String bucketName;

    @Value("${aws.s3.book-covers.region}")
    private String region;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    public S3Service(S3Client s3Client){
        this.s3Client = s3Client;
    }

    private S3Client getS3ClientForRegion(String regionName) {
        if (regionName == null || regionName.isBlank() || regionName.equalsIgnoreCase(region)) {
            return s3Client;
        }
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(regionName));

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    public S3UploadResponse uploadFile(MultipartFile file) throws IOException {
        String key = "book-cover"+ UUID.randomUUID()+ "-"+ file.getOriginalFilename();

        PutObjectRequest request= PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        String url = String.format(
                "https://%s.s3.%s.amazonaws.com/%s",
                bucketName,region,key
        );
        return new S3UploadResponse(key, url);
    }

    public byte[] downloadFile(String bucket, String regionName, String key) {
        try {
            S3Client client = getS3ClientForRegion(regionName);
            ResponseBytes<GetObjectResponse> objectBytes = client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            return objectBytes.asByteArray();
        } catch (Exception e) {
            log.error("Failed to download file from S3: bucket={}, region={}, key={}", bucket, regionName, key, e);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to download file from S3: " + key,
                    e
            );
        }
    }


    public String downloadFileAsString(String bucket, String regionName, String key) {
        try {

            S3Client client = getS3ClientForRegion(regionName);
            ResponseBytes<GetObjectResponse> objectBytes = client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            return objectBytes.asUtf8String();
        } catch (Exception e) {
            log.error("Failed to download text file from S3: bucket={}, region={}, key={}", bucket, regionName, key, e);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to download file from S3: " + key,
                    e
            );
        }
    }

    public String uploadFileBytes(String bucket, String regionName, String key, byte[] bytes, String contentType) {
        try {
            S3Client client = getS3ClientForRegion(regionName);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            client.putObject(request, RequestBody.fromBytes(bytes));
            log.info("Successfully uploaded file bytes to S3: bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("Failed to upload file bytes to S3: bucket={}, region={}, key={}", bucket, regionName, key, e);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload file to S3: " + key,
                    e
            );
        }
    }

}
