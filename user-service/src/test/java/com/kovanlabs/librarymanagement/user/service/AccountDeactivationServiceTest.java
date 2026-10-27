package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeactivationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SalesforceSync salesforceSyncDelegate;

    private AccountDeactivationService deactivationService;

    @BeforeEach
    void setUp() {
        deactivationService = new AccountDeactivationService(userRepository, salesforceSyncDelegate);
    }

    @Test
    @DisplayName("getEffectiveDeactivationDate should prefer deactivatedAt over updatedAt")
    void getEffectiveDeactivationDate_prefersDeactivatedAt() {
        assertNull(deactivationService.getEffectiveDeactivationDate(null));

        LocalDateTime deactivatedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 2, 10, 0);

        User userWithBoth = User.builder().deactivatedAt(deactivatedAt).updatedAt(updatedAt).build();
        assertEquals(deactivatedAt, deactivationService.getEffectiveDeactivationDate(userWithBoth));

        User userWithOnlyUpdated = User.builder().deactivatedAt(null).updatedAt(updatedAt).build();
        assertEquals(updatedAt, deactivationService.getEffectiveDeactivationDate(userWithOnlyUpdated));

        User userWithNeither = User.builder().deactivatedAt(null).updatedAt(null).build();
        assertNull(deactivationService.getEffectiveDeactivationDate(userWithNeither));
    }

    @Test
    @DisplayName("calculateReactivationDeadline should return effective date + 10 days")
    void calculateReactivationDeadline_returnsPlus10Days() {
        assertNull(deactivationService.calculateReactivationDeadline(null));

        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 12, 0);
        User user = User.builder().deactivatedAt(base).build();

        LocalDateTime expected = base.plusDays(10);
        assertEquals(expected, deactivationService.calculateReactivationDeadline(user));
    }

    @Test
    @DisplayName("isReactivationWindowExpired boundary tests")
    void isReactivationWindowExpired_boundaryTests() {
        assertFalse(deactivationService.isReactivationWindowExpired(null));

        User userNoDate = User.builder().build();
        assertFalse(deactivationService.isReactivationWindowExpired(userNoDate));

        LocalDateTime deactivatedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        User user = User.builder().deactivatedAt(deactivatedAt).build();
        LocalDateTime deadline = deactivatedAt.plusDays(10);

        // Before deadline -> not expired
        assertFalse(deactivationService.isReactivationWindowExpired(user, deadline.minusSeconds(1)));
        // Exactly at deadline -> not expired
        assertFalse(deactivationService.isReactivationWindowExpired(user, deadline));
        // After deadline -> expired
        assertTrue(deactivationService.isReactivationWindowExpired(user, deadline.plusSeconds(1)));

        // Test overload using LocalDateTime.now()
        User activeUser = User.builder().deactivatedAt(LocalDateTime.now().minusDays(5)).build();
        assertFalse(deactivationService.isReactivationWindowExpired(activeUser));

        User expiredUser = User.builder().deactivatedAt(LocalDateTime.now().minusDays(11)).build();
        assertTrue(deactivationService.isReactivationWindowExpired(expiredUser));
    }

    @Test
    @DisplayName("isEligibleForPermanentDeactivation rules")
    void isEligibleForPermanentDeactivation_rules() {
        assertFalse(deactivationService.isEligibleForPermanentDeactivation(null));

        User activeUser = User.builder()
                .status(AccountStatus.ACTIVE)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();
        assertFalse(deactivationService.isEligibleForPermanentDeactivation(activeUser));

        // Pending request strictly protects account from permanent deactivation
        User pendingUser = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();
        assertFalse(deactivationService.isEligibleForPermanentDeactivation(pendingUser));

        // Within 10 days -> not eligible
        User withinWindowUser = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(8))
                .build();
        assertFalse(deactivationService.isEligibleForPermanentDeactivation(withinWindowUser));

        // > 10 days without pending request -> eligible
        User eligibleUser = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(12))
                .build();
        assertTrue(deactivationService.isEligibleForPermanentDeactivation(eligibleUser));

        // > 10 days with rejected request -> eligible
        User rejectedUser = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.REJECTED)
                .deactivatedAt(LocalDateTime.now().minusDays(12))
                .build();
        assertTrue(deactivationService.isEligibleForPermanentDeactivation(rejectedUser));
    }

    @Test
    @DisplayName("checkAndTransitionExpiredDeactivation transitions eligible account")
    void checkAndTransitionExpiredDeactivation_transitionsEligible() {
        assertNull(deactivationService.checkAndTransitionExpiredDeactivation(null));

        User activeUser = User.builder().status(AccountStatus.ACTIVE).build();
        assertSame(activeUser, deactivationService.checkAndTransitionExpiredDeactivation(activeUser));

        User pendingUser = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .deactivatedAt(LocalDateTime.now().minusDays(20))
                .build();
        assertSame(pendingUser, deactivationService.checkAndTransitionExpiredDeactivation(pendingUser));

        User withinWindow = User.builder()
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(5))
                .build();
        assertSame(withinWindow, deactivationService.checkAndTransitionExpiredDeactivation(withinWindow));

        User expiredUser = User.builder()
                .id(101L)
                .uuid("u-101")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.REJECTED)
                .deactivatedAt(LocalDateTime.now().minusDays(12))
                .build();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = deactivationService.checkAndTransitionExpiredDeactivation(expiredUser);
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, result.getStatus());
        assertNull(result.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(expiredUser);
        verify(salesforceSyncDelegate).syncContact(any());
    }

    @Test
    @DisplayName("permanentlyDeactivateUser handles validation, race conditions, and persistence")
    void permanentlyDeactivateUser_flow() {
        deactivationService.permanentlyDeactivateUser(null);
        deactivationService.permanentlyDeactivateUser(User.builder().build());

        // Ineligible user in DB
        User ineligible = User.builder()
                .id(102L)
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();
        when(userRepository.findById(102L)).thenReturn(Optional.of(ineligible));
        deactivationService.permanentlyDeactivateUser(ineligible);
        verify(userRepository, never()).save(any());

        // Eligible user
        User eligible = User.builder()
                .id(103L)
                .uuid("u-103")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();
        when(userRepository.findById(103L)).thenReturn(Optional.of(eligible));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deactivationService.permanentlyDeactivateUser(eligible);
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, eligible.getStatus());
        assertNull(eligible.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(eligible);
        verify(salesforceSyncDelegate).syncContact(any());
    }

    @Test
    @DisplayName("processExpiredDeactivations processes batch and skips ineligible")
    void processExpiredDeactivations_batch() {
        when(userRepository.findUsersEligibleForPermanentDeactivation(eq(AccountStatus.DEACTIVATED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        assertEquals(0, deactivationService.processExpiredDeactivations());

        User user1 = User.builder()
                .id(201L)
                .uuid("u-201")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();
        User user2 = User.builder()
                .id(202L)
                .uuid("u-202")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING) // race condition: became pending
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();

        when(userRepository.findUsersEligibleForPermanentDeactivation(eq(AccountStatus.DEACTIVATED), any(LocalDateTime.class)))
                .thenReturn(List.of(user1, user2));
        when(userRepository.findById(201L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(202L)).thenReturn(Optional.of(user2));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = deactivationService.processExpiredDeactivations();
        assertEquals(1, count);
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, user1.getStatus());
        assertEquals(AccountStatus.DEACTIVATED, user2.getStatus());
    }

    @Test
    @DisplayName("syncUserToSalesforce handles success, error, and null delegate")
    void syncUserToSalesforce_coverage() {
        AccountDeactivationService serviceWithoutSF = new AccountDeactivationService(userRepository, null);
        User user = User.builder().id(301L).uuid("u-301").build();
        serviceWithoutSF.syncUserToSalesforce(user);
        serviceWithoutSF.syncUserToSalesforce(null);
        serviceWithoutSF.syncUserToSalesforce(User.builder().build());

        // Successful sync
        user.setSalesforceRetryCount(3);
        deactivationService.syncUserToSalesforce(user);
        assertEquals(SalesforceSyncStatus.SUCCESS, user.getSalesforceSyncStatus());
        assertEquals(0, user.getSalesforceRetryCount());
        verify(salesforceSyncDelegate).syncContact(any());
        verify(userRepository, atLeastOnce()).save(user);

        // Failed sync
        doThrow(new RuntimeException("SF timeout")).when(salesforceSyncDelegate).syncContact(any());
        user.setSalesforceRetryCount(3);
        deactivationService.syncUserToSalesforce(user);
        assertEquals(SalesforceSyncStatus.PENDING, user.getSalesforceSyncStatus());
        assertEquals(1, user.getSalesforceRetryCount());
    }
}
