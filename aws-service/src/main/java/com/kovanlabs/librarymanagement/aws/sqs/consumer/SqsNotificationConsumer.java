package com.kovanlabs.librarymanagement.aws.sqs.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.factory.NotificationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqsNotificationConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final NotificationFactory notificationFactory;

    @Value("${aws.sqs.bookdue-queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:5000}")
    public void consumeMessages() {
        try {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(5)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            if (messages.isEmpty()) {
                return;
            }

            log.info("Polled {} notification message(s) from SQS", messages.size());

            for (Message message : messages) {
                try {
                    NotificationRequest request = objectMapper.readValue(message.body(), NotificationRequest.class);
                    log.info("Received notification request from SQS for recipient: {}", request.recipient());

                    notificationFactory.get(NotificationTypeEnum.EMAIL).send(request);

                    deleteMessage(message.receiptHandle());
                } catch (Exception e) {
                    log.error("Failed to process SQS message [messageId={}]: {}", message.messageId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error receiving messages from SQS queue [queueUrl={}]: {}", queueUrl, e.getMessage(), e);
        }
    }

    private void deleteMessage(String receiptHandle) {
        try {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqsClient.deleteMessage(deleteMessageRequest);
            log.debug("Successfully deleted message from SQS queue");
        } catch (Exception e) {
            log.error("Failed to delete message from SQS queue: {}", e.getMessage(), e);
        }
    }
}
