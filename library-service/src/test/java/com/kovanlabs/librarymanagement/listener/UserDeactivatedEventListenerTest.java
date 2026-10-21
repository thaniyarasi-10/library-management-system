package com.kovanlabs.librarymanagement.listener;

import com.kovanlabs.librarymanagement.common.event.UserPermanentlyDeactivatedEvent;
import com.kovanlabs.librarymanagement.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeactivatedEventListenerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private UserDeactivatedEventListener listener;

    @Test
    void testOnUserPermanentlyDeactivated_success() {
        String userUuid = UUID.randomUUID().toString();
        UserPermanentlyDeactivatedEvent event = new UserPermanentlyDeactivatedEvent(userUuid);

        listener.onUserPermanentlyDeactivated(event);

        verify(paymentService).processPermanentDeactivationRefund(userUuid);
    }

    @Test
    void testOnUserPermanentlyDeactivated_exceptionHandledSafely() {
        String userUuid = UUID.randomUUID().toString();
        UserPermanentlyDeactivatedEvent event = new UserPermanentlyDeactivatedEvent(userUuid);

        when(paymentService.processPermanentDeactivationRefund(userUuid))
                .thenThrow(new RuntimeException("Simulated error"));

        // Should not throw exception
        listener.onUserPermanentlyDeactivated(event);

        verify(paymentService).processPermanentDeactivationRefund(userUuid);
    }
}
