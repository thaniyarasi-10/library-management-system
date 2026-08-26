package com.kovanlabs.librarymanagement.communication.factory;

import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    private NotificationService smsService;

    private NotificationFactory notificationFactory;

    @BeforeEach
    void setUp() {
        when(emailService.getType()).thenReturn(NotificationTypeEnum.EMAIL);
        when(smsService.getType()).thenReturn(NotificationTypeEnum.SMS);

        notificationFactory = new NotificationFactory(List.of(emailService, smsService));
    }

    @Test
    void get_whenEmailType_shouldReturnEmailService() {
        NotificationService service = notificationFactory.get(NotificationTypeEnum.EMAIL);
        assertEquals(emailService, service);
    }

    @Test
    void get_whenSmsType_shouldReturnSmsService() {
        NotificationService service = notificationFactory.get(NotificationTypeEnum.SMS);
        assertEquals(smsService, service);
    }

    @Test
    void get_whenUnsupportedType_shouldThrowIllegalArgumentException() {
        NotificationFactory emptyFactory = new NotificationFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> emptyFactory.get(NotificationTypeEnum.EMAIL));
    }
}
