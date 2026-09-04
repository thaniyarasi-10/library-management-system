package com.kovanlabs.librarymanagement.reward.service;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
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
    private final UserRepository userRepository;

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

        // Increment user reward points using bulk database update
        pointsPerUser.forEach((userUuid, pointsCount) -> {
            userRepository.incrementRewardPoints(userUuid, pointsCount.intValue());
        });

        // Mark all processed borrows as rewardProcessed = true in a single bulk update
        List<UUID> borrowUuids = eligibleBorrows.stream()
                .map(Borrow::getUuid)
                .collect(Collectors.toList());

        int updatedBorrows = borrowRepository.markBorrowsAsRewardProcessed(borrowUuids);

        log.info("Completed processing of on-time return rewards. Incremented points for {} users and updated {} borrows.",
                pointsPerUser.size(), updatedBorrows);

        return updatedBorrows;
    }
}
