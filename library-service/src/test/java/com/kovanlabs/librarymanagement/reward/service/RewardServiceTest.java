package com.kovanlabs.librarymanagement.reward.service;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
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

    @InjectMocks
    private RewardService rewardService;

    private UUID userUuid1;
    private UUID userUuid2;
    private User user1;
    private User user2;
    private Borrow borrow1;
    private Borrow borrow2;
    private Borrow borrow3;

    @BeforeEach
    void setUp() {
        userUuid1 = UUID.randomUUID();
        userUuid2 = UUID.randomUUID();

        user1 = User.builder().uuid(userUuid1).email("user1@example.com").build();
        user2 = User.builder().uuid(userUuid2).email("user2@example.com").build();

        borrow1 = Borrow.builder().uuid(UUID.randomUUID()).user(user1).rewardProcessed(false).build();
        borrow2 = Borrow.builder().uuid(UUID.randomUUID()).user(user1).rewardProcessed(false).build();
        borrow3 = Borrow.builder().uuid(UUID.randomUUID()).user(user2).rewardProcessed(false).build();
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

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(2, processed);
        verify(rewardRepository).incrementPoints(userUuid1, 2);
        verify(rewardRepository, never()).save(any(Reward.class));
        verify(borrowRepository).markBorrowsAsRewardProcessed(List.of(borrow1.getUuid(), borrow2.getUuid()));
    }

    @Test
    void testProcessOnTimeReturnRewards_withNewReward_savesNewRecord() {
        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow3));
        when(rewardRepository.incrementPoints(eq(userUuid2), eq(1))).thenReturn(0);
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(1);

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(1, processed);
        verify(rewardRepository).incrementPoints(userUuid2, 1);

        ArgumentCaptor<Reward> captor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardRepository).save(captor.capture());

        Reward savedReward = captor.getValue();
        assertEquals(userUuid2, savedReward.getUserUuid());
        assertEquals(1, savedReward.getPoints());
    }

    @Test
    void testProcessOnTimeReturnRewards_withNullUserOrNullUserUuid_filtersOutSafely() {
        Borrow borrowWithNullUser = Borrow.builder().uuid(UUID.randomUUID()).user(null).build();
        Borrow borrowWithNullUserUuid = Borrow.builder().uuid(UUID.randomUUID()).user(User.builder().uuid(null).build()).build();

        when(borrowRepository.findUnprocessedOnTimeBorrows()).thenReturn(List.of(borrow1, borrowWithNullUser, borrowWithNullUserUuid));
        when(rewardRepository.incrementPoints(eq(userUuid1), eq(1))).thenReturn(1);
        when(borrowRepository.markBorrowsAsRewardProcessed(any())).thenReturn(3);

        int processed = rewardService.processOnTimeReturnRewards();

        assertEquals(3, processed);
        verify(rewardRepository).incrementPoints(userUuid1, 1);
        verify(rewardRepository, never()).incrementPoints(isNull(), anyInt());
    }
}
