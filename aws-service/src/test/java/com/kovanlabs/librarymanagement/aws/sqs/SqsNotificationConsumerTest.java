package com.kovanlabs.librarymanagement.aws.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kovanlabs.librarymanagement.aws.sqs.consumer.SqsNotificationConsumer;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.communication.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SqsNotificationConsumerTest {

    private SqsClient sqsClient;
    private ObjectMapper objectMapper;
    private NotificationFactory notificationFactory;
    private NotificationService notificationService;
    private SqsNotificationConsumer consumer;

    private final String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789/test-queue";

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        notificationFactory = mock(NotificationFactory.class);
        notificationService = mock(NotificationService.class);

        when(notificationFactory.get(NotificationTypeEnum.EMAIL)).thenReturn(notificationService);

        consumer = new SqsNotificationConsumer(sqsClient, objectMapper, notificationFactory);
        ReflectionTestUtils.setField(consumer, "queueUrl", queueUrl);
    }

    @Test
    @DisplayName("Should process messages from SQS, invoke EmailNotificationService, and delete message")
    void testConsumeMessages_Success() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Overdue Books Notice",
                "Please return overdue books"
        );
        String jsonPayload = objectMapper.writeValueAsString(request);

        Message message = Message.builder()
                .messageId("msg-123")
                .receiptHandle("handle-123")
                .body(jsonPayload)
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        consumer.consumeMessages();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(1)).send(captor.capture());
        assertEquals("user@example.com", captor.getValue().recipient());
        assertEquals("Overdue Books Notice", captor.getValue().subject());

        ArgumentCaptor<DeleteMessageRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient, times(1)).deleteMessage(deleteCaptor.capture());
        assertEquals(queueUrl, deleteCaptor.getValue().queueUrl());
        assertEquals("handle-123", deleteCaptor.getValue().receiptHandle());
    }

    @Test
    @DisplayName("Should handle empty message queue gracefully")
    void testConsumeMessages_EmptyQueue() {
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(Collections.emptyList())
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        consumer.consumeMessages();

        verify(notificationService, never()).send(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Should handle message processing error and continue")
    void testConsumeMessages_ProcessingError() {
        Message message = Message.builder()
                .messageId("msg-bad")
                .receiptHandle("handle-bad")
                .body("invalid-json")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        assertDoesNotThrow(() -> consumer.consumeMessages());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Should handle SQS receive exception gracefully")
    void testConsumeMessages_SqsException() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS Connection Failed"));

        assertDoesNotThrow(() -> consumer.consumeMessages());
    }
}
