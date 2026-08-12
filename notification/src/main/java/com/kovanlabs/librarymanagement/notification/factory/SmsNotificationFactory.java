package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.product.NotificationFormatter;
import com.kovanlabs.librarymanagement.notification.product.NotificationSender;
import com.kovanlabs.librarymanagement.notification.product.SmsFormatter;
import com.kovanlabs.librarymanagement.notification.product.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsNotificationFactory implements NotificationAbstractFactory {

    private final SmsSender smsSender;
    private final SmsFormatter smsFormatter;

    @Override
    public NotificationSender createSender() {
        return smsSender;
    }

    @Override
    public NotificationFormatter createFormatter() {
        return smsFormatter;
    }

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.SMS;
    }
}
