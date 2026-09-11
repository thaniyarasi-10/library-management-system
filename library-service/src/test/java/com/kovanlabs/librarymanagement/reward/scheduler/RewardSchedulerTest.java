package com.kovanlabs.librarymanagement.reward.scheduler;

import com.kovanlabs.librarymanagement.reward.service.RewardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardSchedulerTest {

    @Mock
    private RewardService rewardService;

    @InjectMocks
    private RewardScheduler rewardScheduler;

    @Test
    void testScheduleOnTimeReturnRewards_success() {
        when(rewardService.processOnTimeReturnRewards()).thenReturn(5);

        rewardScheduler.scheduleOnTimeReturnRewards();

        verify(rewardService).processOnTimeReturnRewards();
    }

    @Test
    void testScheduleOnTimeReturnRewards_handlesExceptionGracefully() {
        when(rewardService.processOnTimeReturnRewards()).thenThrow(new RuntimeException("Database error"));

        // Should not throw exception
        rewardScheduler.scheduleOnTimeReturnRewards();

        verify(rewardService).processOnTimeReturnRewards();
    }
}
