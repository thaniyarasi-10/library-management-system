package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.S3UploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    public S3Service(S3Client s3Client){
        this.s3Client = s3Client;
    }

    public S3UploadResponse uploadFile(MultipartFile file) throws IOException {
        String key = "book-cover"+ UUID.randomUUID()+ "-"+ file.getOriginalFilename();

        //where and how to store the file
        PutObjectRequest request= PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        //actually upload here
        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

         String url = String.format(
                "https://%s.s3.%s.amazonaws.com/%s",
                bucketName,region,key
        );
        return new S3UploadResponse(key, url);
    }

}
