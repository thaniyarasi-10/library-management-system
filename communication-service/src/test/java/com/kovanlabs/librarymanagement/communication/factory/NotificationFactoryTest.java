package com.kovanlabs.librarymanagement.communication.factory;

import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFactoryTest {

    @Mock
    private NotificationService emailService;

    private NotificationFactory notificationFactory;

    @BeforeEach
    void setUp() {
        when(emailService.getType()).thenReturn(NotificationTypeEnum.EMAIL);
        notificationFactory = new NotificationFactory(List.of(emailService));
    }

    @Test
    @DisplayName("get(EMAIL) should return registered Email notification service")
    void get_whenEmailType_shouldReturnEmailService() {
        NotificationService service = notificationFactory.get(NotificationTypeEnum.EMAIL);
        assertNotNull(service);
        assertEquals(emailService, service);
    }

    @Test
    @DisplayName("get(SMS) when SMS is not registered should throw IllegalArgumentException")
    void get_whenUnsupportedType_shouldThrowIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> notificationFactory.get(NotificationTypeEnum.SMS));
        assertTrue(exception.getMessage().contains("Notification type not supported: SMS"));
    }

    @Test
    @DisplayName("get with empty factory should throw IllegalArgumentException")
    void get_whenEmptyFactory_shouldThrowIllegalArgumentException() {
        NotificationFactory emptyFactory = new NotificationFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> emptyFactory.get(NotificationTypeEnum.EMAIL));
    }
}
