package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.product.*;
import com.kovanlabs.librarymanagement.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationAbstractFactoryTest {

    private EmailNotificationFactory emailFactory;
    private SmsNotificationFactory smsFactory;
    private SlackNotificationFactory slackFactory;
    private NotificationFactoryProvider provider;
    private NotificationFactory notificationFactoryFacade;

    @BeforeEach
    void setUp() {
        EmailSender emailSender = new EmailSender();
        EmailFormatter emailFormatter = new EmailFormatter();
        emailFactory = new EmailNotificationFactory(emailSender, emailFormatter);

        SmsSender smsSender = new SmsSender();
        SmsFormatter smsFormatter = new SmsFormatter();
        smsFactory = new SmsNotificationFactory(smsSender, smsFormatter);

        SlackSender slackSender = new SlackSender();
        SlackFormatter slackFormatter = new SlackFormatter();
        slackFactory = new SlackNotificationFactory(slackSender, slackFormatter);

        provider = new NotificationFactoryProvider(List.of(emailFactory, smsFactory, slackFactory));
        notificationFactoryFacade = new NotificationFactory(provider);
    }

    @Test
    @DisplayName("EmailNotificationFactory should create EmailSender and EmailFormatter")
    void testEmailNotificationFactory() {
        assertEquals(NotificationTypeEnum.EMAIL, emailFactory.getType());
        assertInstanceOf(EmailSender.class, emailFactory.createSender());
        assertInstanceOf(EmailFormatter.class, emailFactory.createFormatter());

        NotificationFormatter formatter = emailFactory.createFormatter();
        assertEquals("<html><body><p>Hello Email</p></body></html>", formatter.format("Hello Email"));
    }

    @Test
    @DisplayName("SmsNotificationFactory should create SmsSender and SmsFormatter")
    void testSmsNotificationFactory() {
        assertEquals(NotificationTypeEnum.SMS, smsFactory.getType());
        assertInstanceOf(SmsSender.class, smsFactory.createSender());
        assertInstanceOf(SmsFormatter.class, smsFactory.createFormatter());

        NotificationFormatter formatter = smsFactory.createFormatter();
        assertEquals("[SMS]: Hello SMS", formatter.format("Hello SMS"));
    }

    @Test
    @DisplayName("SlackNotificationFactory should create SlackSender and SlackFormatter")
    void testSlackNotificationFactory() {
        assertEquals(NotificationTypeEnum.SLACK, slackFactory.getType());
        assertInstanceOf(SlackSender.class, slackFactory.createSender());
        assertInstanceOf(SlackFormatter.class, slackFactory.createFormatter());

        NotificationFormatter formatter = slackFactory.createFormatter();
        assertEquals("*[SLACK ALERT]*\nHello Slack", formatter.format("Hello Slack"));
    }

    @Test
    @DisplayName("NotificationFactoryProvider should resolve appropriate factory by type")
    void testProviderResolution() {
        assertSame(emailFactory, provider.getFactory(NotificationTypeEnum.EMAIL));
        assertSame(smsFactory, provider.getFactory(NotificationTypeEnum.SMS));
        assertSame(slackFactory, provider.getFactory(NotificationTypeEnum.SLACK));
    }

    @Test
    @DisplayName("NotificationFactory facade should correctly execute notification via abstract factory")
    void testNotificationFactoryFacadeSend() {
        NotificationService service = notificationFactoryFacade.get(NotificationTypeEnum.EMAIL);
        assertNotNull(service);
        assertEquals(NotificationTypeEnum.EMAIL, service.getType());

        NotificationRequest request = new NotificationRequest("user@example.com", "Test Subject", "Test Message");
        assertDoesNotThrow(() -> service.send(request));
    }
}
