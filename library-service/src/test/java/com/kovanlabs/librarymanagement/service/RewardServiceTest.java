package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    private BorrowRepository borrowRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private SalesforceSync salesforceSyncService;

    @InjectMocks
    private RewardService rewardService;

    private String userUuid1;
    private String userUuid2;
    private User user1;
    private User user2;
    private Borrow borrow1;
    private Borrow borrow2;
    private Borrow borrow3;

    @BeforeEach
    void setUp() {
        userUuid1 = UUID.randomUUID().toString();
        userUuid2 = UUID.randomUUID().toString();

        user1 = User.builder().uuid(userUuid1).email("user1@example.com").build();
        user2 = User.builder().uuid(userUuid2).email("user2@example.com").build();

        borrow1 = Borrow.builder().uuid(UUID.randomUUID().toString()).user(user1).rewardProcessed(false).build();
        borrow2 = Borrow.builder().uuid(UUID.randomUUID().toString()).user(user1).rewardProcessed(false).build();
        borrow3 = Borrow.builder().uuid(UUID.randomUUID().toString()).user(user2).rewardProcessed(false).build();
    }

    @Test
    void testProcessOnTimeReturnRewards_whenNoEligibleBorrows_returnsZero() {
        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(Collections.emptyList());

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(0, processed);
        verify(borrowRepository).findUnprocessedOnTimeBorrows();
        verifyNoInteractions(rewardRepository);
        verify(borrowRepository, never()).markBorrowsAsRewardProcessed(any());
    }

    @Test
    void testProcessOnTimeReturnRewards_withExistingReward_incrementsPoints() {
        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow1, borrow2));
        when(rewardRepository.incrementPoints(eq(userUuid1), eq(2))).thenReturn(1);
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(2);
        when(rewardRepository.findByUserUuid(userUuid1)).thenReturn(Optional.of(Reward.builder().userUuid(userUuid1).points(5).build()));

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(2, processed);
        verify(rewardRepository).incrementPoints(userUuid1, 2);
        verify(rewardRepository, never()).save(any(Reward.class));
        verify(salesforceSyncService).syncContact(argThat(c -> userUuid1.equals(c.getExternalUserUuid()) && Integer.valueOf(5).equals(c.getReward())));
        verify(borrowRepository).markBorrowsAsRewardProcessed(List.of(borrow1.getUuid(), borrow2.getUuid()));
    }

    @Test
    void testProcessOnTimeReturnRewards_withNewReward_savesNewRecord() {
        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow3));
        when(rewardRepository.incrementPoints(eq(userUuid2), eq(1))).thenReturn(0);
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(1);
        when(rewardRepository.findByUserUuid(userUuid2)).thenReturn(Optional.of(Reward.builder().userUuid(userUuid2).points(1).build()));

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(1, processed);
        verify(rewardRepository).incrementPoints(userUuid2, 1);
        verify(salesforceSyncService).syncContact(argThat(c -> userUuid2.equals(c.getExternalUserUuid()) && Integer.valueOf(1).equals(c.getReward())));

        ArgumentCaptor<Reward> captor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardRepository).save(captor.capture());

        Reward savedReward = captor.getValue();
        assertEquals(userUuid2, savedReward.getUserUuid());
        assertEquals(1, savedReward.getPoints());
    }

    @Test
    void testProcessOnTimeReturnRewards_withNullUserOrNullUserUuid_filtersOutSafely() {
        Borrow borrowWithNullUser = Borrow.builder().uuid(UUID.randomUUID().toString()).user(null).build();
        Borrow borrowWithNullUserUuid = Borrow.builder().uuid(UUID.randomUUID().toString()).user(User.builder().uuid(null).build()).build();

        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow1, borrowWithNullUser, borrowWithNullUserUuid));
        when(rewardRepository.incrementPoints(eq(userUuid1), eq(1))).thenReturn(1);
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(3);

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(3, processed);
        verify(rewardRepository).incrementPoints(userUuid1, 1);
        verify(rewardRepository, never()).incrementPoints(isNull(), anyInt());
    }

    @Test
    void testProcessOnTimeReturnRewards_whenSalesforceThrows_shouldLogAndNotThrow() {
        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow1));
        when(rewardRepository.incrementPoints(eq(userUuid1), eq(1))).thenReturn(1);
        when(rewardRepository.findByUserUuid(userUuid1)).thenReturn(Optional.of(Reward.builder().userUuid(userUuid1).points(2).build()));
        doThrow(new RuntimeException("SF sync error")).when(salesforceSyncService).syncContact(any());
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(1);

        assertDoesNotThrow(() -> rewardService.processOnTimeReturnRewards());
        verify(borrowRepository).markBorrowsAsRewardProcessed(any());
    }

    @Test
    void testProcessOnTimeReturnRewards_withDeactivatedUser_doesNotAwardRewardPoints() {
        User deactUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .email("deact@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();
        Borrow deactBorrow = Borrow.builder()
                .uuid(UUID.randomUUID().toString())
                .user(deactUser)
                .rewardProcessed(false)
                .build();

        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(deactBorrow));
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(1);

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(1, processed);
        verify(rewardRepository, never()).incrementPoints(any(), anyInt());
        verify(rewardRepository, never()).save(any(Reward.class));
        verifyNoInteractions(salesforceSyncService);
    }

    @Test
    void testProcessOnTimeReturnRewards_withReactivatedUser_awardsRewardPointsNormally() {
        String reactivatedUuid = UUID.randomUUID().toString();
        User reactivatedUser = User.builder()
                .uuid(reactivatedUuid)
                .email("reactivated@example.com")
                .status(AccountStatus.ACTIVE)
                .build();
        Borrow reactivatedBorrow = Borrow.builder()
                .uuid(UUID.randomUUID().toString())
                .user(reactivatedUser)
                .rewardProcessed(false)
                .build();

        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(reactivatedBorrow));
        when(rewardRepository.incrementPoints(eq(reactivatedUuid), eq(1))).thenReturn(1);
        when(rewardRepository.findByUserUuid(reactivatedUuid))
                .thenReturn(Optional.of(Reward.builder().userUuid(reactivatedUuid).points(1).build()));
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(1);

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(1, processed);
        verify(rewardRepository).incrementPoints(reactivatedUuid, 1);
        verify(salesforceSyncService).syncContact(argThat(c -> reactivatedUuid.equals(c.getExternalUserUuid()) && Integer.valueOf(1).equals(c.getReward())));
    }
}
