package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmsNotificationServiceTest {

    private SmsNotificationService smsNotificationService;

    @BeforeEach
    void setUp() {
        smsNotificationService = new SmsNotificationService();
    }

    @Test
    @DisplayName("getType should return SMS")
    void getType_shouldReturnSms() {
        assertEquals(NotificationTypeEnum.SMS, smsNotificationService.getType());
    }

    @Test
    @DisplayName("send SMS with valid recipient and message")
    void send_ValidRecipientAndMessage() {
        NotificationRequest request = new NotificationRequest(
                "+1234567890",
                "SMS Alert",
                "Your book is overdue"
        );

        assertDoesNotThrow(() -> smsNotificationService.send(request));
    }

    @Test
    @DisplayName("send SMS with null recipient or message handles without throwing exception")
    void send_NullRecipientOrMessage() {
        NotificationRequest nullRecipient = new NotificationRequest(null, "Subject", "Message");
        NotificationRequest nullMessage = new NotificationRequest("+12345", "Subject", null);

        assertDoesNotThrow(() -> smsNotificationService.send(nullRecipient));
        assertDoesNotThrow(() -> smsNotificationService.send(nullMessage));
    }
}
