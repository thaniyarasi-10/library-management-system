package com.kovanlabs.librarymanagement.reward.service;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final BorrowRepository borrowRepository;
    private final RewardRepository rewardRepository;

    @Transactional
    public int processOnTimeReturnRewards() {
        log.info("Starting processing of on-time return rewards");
        List<Borrow> eligibleBorrows = borrowRepository.findUnprocessedOnTimeBorrows();

        if (eligibleBorrows.isEmpty()) {
            log.info("No unprocessed on-time returned borrows found for reward processing.");
            return 0;
        }

        // Group eligible borrows by User UUID to aggregate total points earned per user
        Map<UUID, Long> pointsPerUser = eligibleBorrows.stream()
                .filter(b -> b.getUser() != null && b.getUser().getUuid() != null)
                .collect(Collectors.groupingBy(b -> b.getUser().getUuid(), Collectors.counting()));

        // Increment user reward points using rewardRepository
        pointsPerUser.forEach((userUuid, pointsCount) -> {
            int updated = rewardRepository.incrementPoints(userUuid, pointsCount.intValue());
            if (updated == 0) {
                // If reward row does not exist for this user, insert a new record
                Reward newReward = Reward.builder()
                        .userUuid(userUuid)
                        .points(pointsCount.intValue())
                        .build();
                rewardRepository.save(newReward);
            }
        });

        // Mark all processed borrows as rewardProcessed = true in a single bulk update
        List<UUID> borrowUuids = eligibleBorrows.stream()
                .map(Borrow::getUuid)
                .collect(Collectors.toList());

        int updatedBorrows = borrowRepository.markBorrowsAsRewardProcessed(borrowUuids);

        log.info("Completed processing of on-time return rewards. Updated rewards for {} users and marked {} borrows.",
                pointsPerUser.size(), updatedBorrows);

        return updatedBorrows;
    }
}
