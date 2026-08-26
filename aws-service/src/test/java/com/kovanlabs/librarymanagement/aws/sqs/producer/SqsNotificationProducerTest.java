package com.kovanlabs.librarymanagement.aws.sqs.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsNotificationProducerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    private SqsNotificationProducer producer;

    private final String queueUrl = "https://sqs.us-east-1.amazonaws.com/12345/test-queue";

    @BeforeEach
    void setUp() {
        producer = new SqsNotificationProducer(sqsClient, objectMapper);
        ReflectionTestUtils.setField(producer, "queueUrl", queueUrl);
    }

    @Test
    void send_shouldSerializeAndSendMessage() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Due Notice",
                "Book due soon",
                "John",
                null,
                0.0
        );

        when(objectMapper.writeValueAsString(request)).thenReturn("{\"recipient\":\"user@example.com\"}");

        producer.send(request);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient, times(1)).sendMessage(captor.capture());
        assertEquals(queueUrl, captor.getValue().queueUrl());
        assertEquals("{\"recipient\":\"user@example.com\"}", captor.getValue().messageBody());
    }

    @Test
    void send_whenSerializationFails_shouldThrowRuntimeException() throws Exception {
        NotificationRequest request = new NotificationRequest("user@example.com", "Sub", "Msg");
        when(objectMapper.writeValueAsString(request)).thenThrow(new JsonProcessingException("Serialization error") {});

        RuntimeException exception = assertThrows(RuntimeException.class, () -> producer.send(request));
        assertTrue(exception.getMessage().contains("Failed to serialize notification"));
    }

    @Test
    void send_whenSqsFails_shouldThrowRuntimeException() throws Exception {
        NotificationRequest request = new NotificationRequest("user@example.com", "Sub", "Msg");
        when(objectMapper.writeValueAsString(request)).thenReturn("{}");
        doThrow(SqsException.builder().message("SQS error").build()).when(sqsClient).sendMessage(any(SendMessageRequest.class));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> producer.send(request));
        assertTrue(exception.getMessage().contains("Failed to send notification to SQS"));
    }
}
