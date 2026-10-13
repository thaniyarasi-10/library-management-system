package com.kovanlabs.librarymanagement.inactivity.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.communication.service.NotificationService;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.service.AccountInactivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountInactivityServiceTest {

    private NotificationFactory notificationFactory;
    private NotificationService emailNotificationService;
    private UserRepository userRepository;
    private AccountInactivityService service;

    @BeforeEach
    void setUp() {
        notificationFactory = mock(NotificationFactory.class);
        emailNotificationService = mock(NotificationService.class);
        userRepository = mock(UserRepository.class);

        when(notificationFactory.get(NotificationTypeEnum.EMAIL)).thenReturn(emailNotificationService);

        service = new AccountInactivityService(
                notificationFactory,
                userRepository
        );
    }

    @Test
    @DisplayName("processUser with null user or non-ACTIVE user should do nothing")
    void processUser_nullOrInactive_shouldDoNothing() {
        service.processUser(null);

        User deactivatedUser = User.builder()
                .id(1L)
                .email("deact@test.com")
                .status(AccountStatus.DEACTIVATED)
                .build();
        service.processUser(deactivatedUser);

        verifyNoInteractions(notificationFactory);
        verifyNoInteractions(emailNotificationService);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("processUser inactive >= 30 days should deactivate user in MySQL and send deactivation email")
    void processUser_inactivity30Days_shouldDeactivateAndSendEmail() {
        User user = User.builder()
                .id(2L)
                .uuid("u-2")
                .email("inactive@test.com")
                .name("Inactive User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(31))
                .build();

        service.processUser(user);

        assertEquals(AccountStatus.DEACTIVATED, user.getStatus());
        assertNotNull(user.getDeactivatedAt());
        verify(userRepository).save(user);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailNotificationService, times(1)).send(captor.capture());
        NotificationRequest sent = captor.getValue();
        assertEquals("inactive@test.com", sent.recipient());
        assertEquals(EmailTemplateType.ACCOUNT_DEACTIVATED, sent.templateType());
        assertTrue(sent.inactiveDays() >= 30);
    }

    @Test
    @DisplayName("processUser inactive >= 30 days when lastLoginAt is null should use createdAt and send deactivation email")
    void processUser_nullLastLogin_shouldUseCreatedAtAndDeactivate() {
        User user = User.builder()
                .id(3L)
                .uuid("u-3")
                .email("neverlogged@test.com")
                .name("New Inactive User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(null)
                .createdAt(LocalDateTime.now().minusDays(32))
                .build();

        service.processUser(user);

        assertEquals(AccountStatus.DEACTIVATED, user.getStatus());
        assertNotNull(user.getDeactivatedAt());
        verify(userRepository).save(user);

        verify(emailNotificationService, times(1)).send(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("processUser at warning threshold (23 to 29 days) should send warning email via factory")
    void processUser_warningThreshold_shouldSendWarningEmail() {
        User user = User.builder()
                .id(4L)
                .uuid("u-4")
                .email("warn@test.com")
                .name("Warn User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(24))
                .build();

        service.processUser(user);

        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getInactivityWarningSentAt());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailNotificationService, times(1)).send(captor.capture());
        NotificationRequest sent = captor.getValue();
        assertEquals("warn@test.com", sent.recipient());
        assertEquals("Warn User", sent.userName());
        assertEquals(EmailTemplateType.INACTIVITY_WARNING, sent.templateType());
        assertTrue(sent.inactiveDays() >= 23);

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("processUser at warning threshold when warning already sent should skip duplicate email")
    void processUser_warningAlreadySent_shouldNotResend() {
        User user = User.builder()
                .id(5L)
                .uuid("u-5")
                .email("alreadywarned@test.com")
                .name("Warned User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(25))
                .inactivityWarningSentAt(LocalDateTime.now().minusDays(2))
                .build();

        service.processUser(user);

        verifyNoInteractions(notificationFactory);
        verifyNoInteractions(emailNotificationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("processUser when sending email throws exception should handle gracefully without crashing")
    void processUser_warningThrowsException_shouldHandleGracefully() {
        User user = User.builder()
                .id(6L)
                .uuid("u-6")
                .email("failwarn@test.com")
                .name("Fail User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(24))
                .build();

        doThrow(new RuntimeException("Email dispatch error")).when(emailNotificationService).send(any());

        assertDoesNotThrow(() -> service.processUser(user));
        assertNull(user.getInactivityWarningSentAt());
    }

    @Test
    @DisplayName("processUser when deactivation email throws exception should still deactivate user")
    void processUser_deactivateEmailThrowsException_shouldStillDeactivate() {
        User user = User.builder()
                .id(66L)
                .uuid("u-66")
                .email("faildeactmail@test.com")
                .name("Fail Deact Mail")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(35))
                .build();

        doThrow(new RuntimeException("Deactivation email error")).when(emailNotificationService).send(any());

        assertDoesNotThrow(() -> service.processUser(user));
        assertEquals(AccountStatus.DEACTIVATED, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("processUser active (< 23 days) with previously sent warning should reset warning state")
    void processUser_activeUser_shouldResetWarningTimestamp() {
        User user = User.builder()
                .id(7L)
                .uuid("u-7")
                .email("activeagain@test.com")
                .name("Active User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(5))
                .inactivityWarningSentAt(LocalDateTime.now().minusDays(10))
                .build();

        service.processUser(user);

        assertNull(user.getInactivityWarningSentAt());
        verify(userRepository).save(user);
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    @DisplayName("processUser active (< 23 days) without warning should not trigger database save")
    void processUser_activeUserWithoutWarning_shouldNotSave() {
        User user = User.builder()
                .id(8L)
                .uuid("u-8")
                .email("pureactive@test.com")
                .name("Active User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(2))
                .build();

        service.processUser(user);

        verifyNoInteractions(notificationFactory);
        verifyNoInteractions(emailNotificationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("determineInactivityBaseline when both lastLoginAt and createdAt are null should fallback safely")
    void processUser_nullLoginAndCreated_shouldFallbackSafely() {
        User user = User.builder()
                .id(9L)
                .uuid("u-9")
                .email("nobaseline@test.com")
                .name("No Baseline")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(null)
                .createdAt(null)
                .build();

        service.processUser(user);

        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    @DisplayName("processInactiveAccounts should process deactivations, warnings, and warning resets")
    void processInactiveAccounts_success() {
        User userToDeactivate = User.builder()
                .id(10L)
                .uuid("u-10")
                .email("deactivate@test.com")
                .name("To Deactivate")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(35))
                .build();

        User userToWarn = User.builder()
                .id(11L)
                .uuid("u-11")
                .email("warnme@test.com")
                .name("To Warn")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(25))
                .build();

        User userToReset = User.builder()
                .id(12L)
                .uuid("u-12")
                .email("resetwarn@test.com")
                .name("To Reset")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(2))
                .inactivityWarningSentAt(LocalDateTime.now().minusDays(10))
                .build();

        when(userRepository.findUsersEligibleForDeactivation(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(userToDeactivate));
        when(userRepository.findUsersEligibleForInactivityWarning(eq(AccountStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(userToWarn));
        when(userRepository.findActiveUsersWithPendingWarningToReset(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(userToReset));

        service.processInactiveAccounts();

        assertEquals(AccountStatus.DEACTIVATED, userToDeactivate.getStatus());
        assertNotNull(userToDeactivate.getDeactivatedAt());
        verify(userRepository).save(userToDeactivate);

        assertEquals(AccountStatus.ACTIVE, userToWarn.getStatus());
        assertNotNull(userToWarn.getInactivityWarningSentAt());
        verify(userRepository).save(userToWarn);

        assertNull(userToReset.getInactivityWarningSentAt());
        verify(userRepository).save(userToReset);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailNotificationService, times(2)).send(captor.capture());

        List<NotificationRequest> sentRequests = captor.getAllValues();
        assertEquals(2, sentRequests.size());

        NotificationRequest deactReq = sentRequests.stream()
                .filter(r -> r.templateType() == EmailTemplateType.ACCOUNT_DEACTIVATED)
                .findFirst().orElseThrow();
        assertEquals("deactivate@test.com", deactReq.recipient());

        NotificationRequest warnReq = sentRequests.stream()
                .filter(r -> r.templateType() == EmailTemplateType.INACTIVITY_WARNING)
                .findFirst().orElseThrow();
        assertEquals("warnme@test.com", warnReq.recipient());
    }

    @Test
    @DisplayName("processInactiveAccounts when empty lists returned should complete cleanly without interactions")
    void processInactiveAccounts_emptyLists() {
        when(userRepository.findUsersEligibleForDeactivation(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userRepository.findUsersEligibleForInactivityWarning(eq(AccountStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userRepository.findActiveUsersWithPendingWarningToReset(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> service.processInactiveAccounts());

        verify(emailNotificationService, never()).send(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("processInactiveAccounts should catch exceptions on individual user processing without aborting batch")
    void processInactiveAccounts_isolatedExceptions() {
        User failingUser = mock(User.class);
        when(failingUser.getId()).thenReturn(99L);
        when(failingUser.getEmail()).thenReturn("fail@test.com");
        when(failingUser.getLastLoginAt()).thenThrow(new RuntimeException("Simulated error in getter"));

        User succeedingUser = User.builder()
                .id(100L)
                .uuid("u-100")
                .email("success@test.com")
                .name("Success User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(32))
                .build();

        when(userRepository.findUsersEligibleForDeactivation(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(failingUser, succeedingUser));
        when(userRepository.findUsersEligibleForInactivityWarning(eq(AccountStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userRepository.findActiveUsersWithPendingWarningToReset(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> service.processInactiveAccounts());

        assertEquals(AccountStatus.DEACTIVATED, succeedingUser.getStatus());
        verify(userRepository).save(succeedingUser);
        verify(emailNotificationService, times(1)).send(any());
    }

    @Test
    @DisplayName("processInactiveAccounts warning batch should catch individual exceptions and continue")
    void processInactiveAccounts_warningIsolatedExceptions() {
        User failingWarnUser = mock(User.class);
        when(failingWarnUser.getId()).thenReturn(88L);
        when(failingWarnUser.getEmail()).thenReturn("failwarn@test.com");
        when(failingWarnUser.getLastLoginAt()).thenThrow(new RuntimeException("Simulated warning baseline error"));

        User succeedingWarnUser = User.builder()
                .id(89L)
                .uuid("u-89")
                .email("successwarn@test.com")
                .name("Success Warn User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(26))
                .build();

        when(userRepository.findUsersEligibleForDeactivation(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userRepository.findUsersEligibleForInactivityWarning(eq(AccountStatus.ACTIVE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(failingWarnUser, succeedingWarnUser));
        when(userRepository.findActiveUsersWithPendingWarningToReset(eq(AccountStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> service.processInactiveAccounts());

        assertEquals(AccountStatus.ACTIVE, succeedingWarnUser.getStatus());
        assertNotNull(succeedingWarnUser.getInactivityWarningSentAt());
        verify(userRepository).save(succeedingWarnUser);
        verify(emailNotificationService, times(1)).send(any());
    }
}
