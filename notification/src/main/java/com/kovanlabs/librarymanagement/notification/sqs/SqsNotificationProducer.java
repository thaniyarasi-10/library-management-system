package com.kovanlabs.librarymanagement.notification.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Service
@RequiredArgsConstructor
public class SqsNotificationProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.bookdue-queue-url}")
    private String queueUrl;

    public void send(NotificationRequest notification) {

        try {
            String message = objectMapper.writeValueAsString(notification);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message)
                    .build();

            sqsClient.sendMessage(request);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification", e);
        }catch (SqsException e) {
            throw new RuntimeException("Failed to send notification to SQS", e);
        }
    }
}