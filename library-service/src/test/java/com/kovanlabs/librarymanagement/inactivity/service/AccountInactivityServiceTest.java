package com.kovanlabs.librarymanagement.inactivity.service;

import com.kovanlabs.librarymanagement.communication.service.InactivityNotificationService;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountInactivityServiceTest {

    private InactivityNotificationService notificationService;
    private UserRepository userRepository;
    private AccountInactivityService service;

    @BeforeEach
    void setUp() {
        notificationService = mock(InactivityNotificationService.class);
        userRepository = mock(UserRepository.class);

        service = new AccountInactivityService(
                notificationService,
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

        verifyNoInteractions(notificationService);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("processUser inactive >= 30 days should deactivate user in MySQL")
    void processUser_inactivity30Days_shouldDeactivate() {
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
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("processUser inactive >= 30 days when lastLoginAt is null should use createdAt")
    void processUser_nullLastLogin_shouldUseCreatedAt() {
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
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("processUser at warning threshold (23 to 29 days) should send warning email")
    void processUser_warningThreshold_shouldSendWarning() {
        User user = User.builder()
                .id(4L)
                .uuid("u-4")
                .email("warn@test.com")
                .name("Warn User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(24))
                .build();

        when(notificationService.sendInactivityWarning(
                eq("warn@test.com"),
                eq("Warn User"),
                anyLong(),
                anyLong(),
                any(LocalDate.class)
        )).thenReturn(true);

        service.processUser(user);

        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getInactivityWarningSentAt());
        verify(notificationService).sendInactivityWarning(
                eq("warn@test.com"),
                eq("Warn User"),
                anyLong(),
                anyLong(),
                any(LocalDate.class)
        );
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

        verifyNoInteractions(notificationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("processUser when warning email sending fails should not persist warning timestamp")
    void processUser_warningFails_shouldNotPersistTimestamp() {
        User user = User.builder()
                .id(6L)
                .uuid("u-6")
                .email("failwarn@test.com")
                .name("Fail User")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(LocalDateTime.now().minusDays(24))
                .build();

        when(notificationService.sendInactivityWarning(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn(false);

        service.processUser(user);

        assertNull(user.getInactivityWarningSentAt());
        verify(userRepository, never()).save(user);
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

        verifyNoInteractions(notificationService);
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
    }
}
