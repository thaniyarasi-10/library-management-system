package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.product.NotificationFormatter;
import com.kovanlabs.librarymanagement.notification.product.NotificationSender;
import com.kovanlabs.librarymanagement.notification.product.SlackFormatter;
import com.kovanlabs.librarymanagement.notification.product.SlackSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackNotificationFactory implements NotificationAbstractFactory {

    private final SlackSender slackSender;
    private final SlackFormatter slackFormatter;

    @Override
    public NotificationSender createSender() {
        return slackSender;
    }

    @Override
    public NotificationFormatter createFormatter() {
        return slackFormatter;
    }

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.SLACK;
    }
}
