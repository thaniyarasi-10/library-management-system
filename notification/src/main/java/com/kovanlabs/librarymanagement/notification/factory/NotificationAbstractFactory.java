package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.product.NotificationFormatter;
import com.kovanlabs.librarymanagement.notification.product.NotificationSender;

public interface NotificationAbstractFactory {
    NotificationSender createSender();
    NotificationFormatter createFormatter();
    NotificationTypeEnum getType();
}
