package com.kovanlabs.librarymanagement.listener;

import com.kovanlabs.librarymanagement.common.event.UserPermanentlyDeactivatedEvent;
import com.kovanlabs.librarymanagement.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeactivatedEventListener {

    private final PaymentService paymentService;

    @EventListener
    public void onUserPermanentlyDeactivated(UserPermanentlyDeactivatedEvent event) {
        log.info("Received UserPermanentlyDeactivatedEvent for userUuid: {}", event.userUuid());
        try {
            paymentService.processPermanentDeactivationRefund(event.userUuid());
        } catch (Exception e) {
            log.error("Failed to process refund for permanently deactivated user [{}]: {}",
                    event.userUuid(), e.getMessage(), e);
        }
    }
}
