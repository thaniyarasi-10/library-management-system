package com.kovanlabs.librarymanagement.reward.scheduler;

import com.kovanlabs.librarymanagement.reward.service.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RewardScheduler {

    private final RewardService rewardService;

    // Run every day at midnight (00:00:00)
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduleOnTimeReturnRewards() {
        log.info("Cron triggered: Running daily reward calculation for on-time returns");
        try {
            int count = rewardService.processOnTimeReturnRewards();
            log.info("Cron completed: {} rewards generated", count);
        } catch (Exception e) {
            log.error("Error during scheduled reward calculation", e);
        }
    }
}
