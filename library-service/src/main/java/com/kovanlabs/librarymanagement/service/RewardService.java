package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for calculating and awarding reward points to users for returning books on time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final BorrowRepository borrowRepository;
    private final RewardRepository rewardRepository;
    private final SalesforceSync salesforceSyncService;

    /**
     * Finds unprocessed on-time returned borrows, aggregates points per user,
     * awards reward points, and marks borrow entries as processed.
     *
     * @return Total count of borrow records processed and rewarded
     */
    @Transactional
    public int processOnTimeReturnRewards() {
        log.info("Starting processing of on-time return rewards");
        List<Borrow> eligibleBorrows = borrowRepository.findUnprocessedOnTimeBorrows();

        if (eligibleBorrows.isEmpty()) {
            log.info("No unprocessed on-time returned borrows found for reward processing.");
            return 0;
        }

        // Group eligible borrows by User UUID to aggregate total points earned per user
        Map<String, Long> pointsPerUser = eligibleBorrows.stream()
                .filter(b -> Objects.nonNull(b.getUser())
                        && Objects.nonNull(b.getUser().getUuid())
                        && b.getUser().getStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.groupingBy(b -> b.getUser().getUuid(), Collectors.counting()));

        // Increment user reward points using rewardRepository and sync to Salesforce
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
            syncRewardToSalesforce(userUuid);
        });

        // Mark all processed borrows as rewardProcessed = true in a single bulk update
        List<String> borrowUuids = eligibleBorrows.stream()
                .map(Borrow::getUuid)
                .collect(Collectors.toList());

        int updatedBorrows = borrowRepository.markBorrowsAsRewardProcessed(borrowUuids);

        log.info("Completed processing of on-time return rewards. Updated rewards for {} users and marked {} borrows.",
                pointsPerUser.size(), updatedBorrows);

        return updatedBorrows;
    }

    private void syncRewardToSalesforce(String userUuid) {
        if (Objects.nonNull(salesforceSyncService) && Objects.nonNull(userUuid)) {
            try {
                int totalPoints = rewardRepository.findByUserUuid(userUuid)
                        .map(Reward::getPoints)
                        .orElse(0);
                salesforceSyncService.syncContact(ContactSObject.builder()
                        .externalUserUuid(userUuid)
                        .reward(totalPoints)
                        .build());
                log.info("Synchronized reward points ({}) to Salesforce for user [{}]", totalPoints, userUuid);
            } catch (Exception e) {
                log.error("Failed to sync reward points to Salesforce for user [UUID: {}]: {}", userUuid, e.getMessage());
            }
        }
    }
}
