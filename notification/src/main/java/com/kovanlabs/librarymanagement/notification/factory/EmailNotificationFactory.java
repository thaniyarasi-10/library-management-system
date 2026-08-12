package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.product.EmailFormatter;
import com.kovanlabs.librarymanagement.notification.product.EmailSender;
import com.kovanlabs.librarymanagement.notification.product.NotificationFormatter;
import com.kovanlabs.librarymanagement.notification.product.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationFactory implements NotificationAbstractFactory {

    private final EmailSender emailSender;
    private final EmailFormatter emailFormatter;

    @Override
    public NotificationSender createSender() {
        return emailSender;
    }

    @Override
    public NotificationFormatter createFormatter() {
        return emailFormatter;
    }

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.EMAIL;
    }
}
