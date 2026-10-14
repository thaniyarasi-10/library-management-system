package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.communication.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Service managing user account inactivity lifecycles, warning dispatches,
 * automatic account deactivations, and external Salesforce CRM synchronization.
 * <p>
 * Business rules:
 * <ul>
 *     <li>Inactivity period threshold: 30 days. Accounts inactive for &ge; 30 days are automatically transitioned
 *         to {@link AccountStatus#DEACTIVATED} in both MySQL and Salesforce.</li>
 *     <li>Advance warning notice: Sent at 23 days of inactivity (7 days before deactivation cutoff).
 *         Records {@code inactivityWarningSentAt} in both MySQL and Salesforce to prevent repeated notifications.</li>
 *     <li>Stale warning reset: Active users who log back in have their warning timestamps cleared and synchronized.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountInactivityService {

    public static final int INACTIVITY_DAYS = 30;

    public static final int WARNING_DAYS_BEFORE = 7;

    public static final int WARNING_THRESHOLD_DAYS = INACTIVITY_DAYS - WARNING_DAYS_BEFORE;

    private final NotificationFactory notificationFactory;
    private final UserRepository userRepository;
    private final RewardRepository rewardRepository;
    private final SalesforceSync salesforceSyncDelegate;

    /**
     * Executes the daily batch processing of account inactivity using optimized, targeted database queries.
     * <p>
     * Instead of scanning every active user, this method queries MySQL directly for:
     * <ol>
     *     <li>Users inactive for &ge; 30 days (deactivates them, syncs to Salesforce, and sends deactivation notice).</li>
     *     <li>Users inactive for &ge; 23 and &lt; 30 days without prior warning (sends warning notice, sets timestamp, and syncs).</li>
     *     <li>Active users (&lt; 23 days) who logged back in (clears stale warning timestamps and syncs to Salesforce).</li>
     * </ol>
     */
    @Transactional
    public void processInactiveAccounts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deactivationCutoff = now.minusDays(INACTIVITY_DAYS);
        LocalDateTime warningCutoff = now.minusDays(WARNING_THRESHOLD_DAYS);

        log.info("Starting optimized batch account inactivity processing (Warning Cutoff: {}, Deactivation Cutoff: {})",
                warningCutoff, deactivationCutoff);

        // 1. Process users eligible for deactivation (>= 30 days inactive)
        List<User> usersToDeactivate = userRepository.findUsersEligibleForDeactivation(AccountStatus.ACTIVE, deactivationCutoff);
        log.info("Found {} active user(s) eligible for automatic deactivation", usersToDeactivate.size());
        for (User user : usersToDeactivate) {
            try {
                LocalDateTime baseline = determineInactivityBaseline(user);
                long inactiveDays = Duration.between(baseline, now).toDays();
                deactivateUser(user, inactiveDays);
            } catch (Exception e) {
                log.error("Failed to deactivate inactive user ID {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        // 2. Process users eligible for warning (>= 23 days and < 30 days, no warning sent)
        List<User> usersToWarn = userRepository.findUsersEligibleForInactivityWarning(AccountStatus.ACTIVE, warningCutoff, deactivationCutoff);
        log.info("Found {} active user(s) eligible for inactivity warning notice", usersToWarn.size());
        for (User user : usersToWarn) {
            try {
                LocalDateTime baseline = determineInactivityBaseline(user);
                long inactiveDays = Duration.between(baseline, now).toDays();
                handleWarning(user, inactiveDays);
            } catch (Exception e) {
                log.error("Failed to send warning email to user ID {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        // 3. Reset warning timestamps for users who became active again (< 23 days)
        List<User> usersToReset = userRepository.findActiveUsersWithPendingWarningToReset(AccountStatus.ACTIVE, warningCutoff);
        if (!usersToReset.isEmpty()) {
            log.info("Resetting inactivity warning timestamps for {} user(s) who logged back in", usersToReset.size());
            for (User user : usersToReset) {
                user.setInactivityWarningSentAt(null);
                userRepository.save(user);
                syncUserToSalesforce(user);
            }
        }

        log.info("Completed batch account inactivity processing successfully");
    }

    /**
     * Evaluates and processes an individual user entity for account inactivity warnings or automatic deactivation.
     *
     * @param user The {@link User} entity to evaluate. If {@code null} or status is not {@link AccountStatus#ACTIVE}, processing is safely skipped.
     */
    @Transactional
    public void processUser(User user) {
        if (Objects.isNull(user) || user.getStatus() != AccountStatus.ACTIVE) {
            return;
        }

        LocalDateTime baseline = determineInactivityBaseline(user);
        long inactiveDays = Duration.between(baseline, LocalDateTime.now()).toDays();

        // Case 1: Inactivity reached or exceeded deadline (>= 30 days) -> DEACTIVATE & SEND DEACTIVATION MAIL
        if (inactiveDays >= INACTIVITY_DAYS) {
            deactivateUser(user, inactiveDays);
            return;
        }

        // Case 2: Inactivity reached warning threshold (>= 23 days and < 30 days) -> SEND WARNING MAIL
        if (inactiveDays >= WARNING_THRESHOLD_DAYS) {
            handleWarning(user, inactiveDays);
            return;
        }

        // Case 3: User is active (< 23 days). Clear any previous warning timestamp
        if (Objects.nonNull(user.getInactivityWarningSentAt())) {
            user.setInactivityWarningSentAt(null);
            userRepository.save(user);
            syncUserToSalesforce(user);
        }
    }

    /**
     * Deactivates an inactive user account in MySQL, synchronizes the DEACTIVATED status to Salesforce,
     * and dispatches a deactivation notice email.
     *
     * @param user         The user entity to deactivate
     * @param inactiveDays Total elapsed inactive days
     */
    private void deactivateUser(User user, long inactiveDays) {
        log.info("User {} (ID: {}) inactive for {} days (threshold: {}). Deactivating account in MySQL.",
                user.getEmail(), user.getId(), inactiveDays, INACTIVITY_DAYS);

        user.setStatus(AccountStatus.DEACTIVATED);
        user.setReactivationRequestStatus(null);
        if (Objects.nonNull(rewardRepository) && Objects.nonNull(user.getUuid())) {
            int updated = rewardRepository.resetPoints(user.getUuid());
            if (updated == 0) {
                rewardRepository.save(Reward.builder()
                        .userUuid(user.getUuid())
                        .points(0)
                        .build());
            }
            log.info("Reset reward points to 0 in MySQL for inactive user [ID: {}, UUID: {}]", user.getId(), user.getUuid());
        }
        userRepository.save(user);
        syncUserToSalesforce(user);

        log.info("Successfully deactivated user account in MySQL and initiated Salesforce sync [ID: {}, Email: {}]",
                user.getId(), user.getEmail());

        try {
            NotificationRequest deactivationNotice = new NotificationRequest(
                    user.getEmail(),
                    "Notice: Account Deactivated - Library Management System",
                    null,
                    user.getName(),
                    null,
                    null,
                    EmailTemplateType.ACCOUNT_DEACTIVATED,
                    inactiveDays,
                    null,
                    null
            );
            notificationFactory.get(NotificationTypeEnum.EMAIL).send(deactivationNotice);
            log.info("Dispatched account deactivation email notification to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send account deactivation email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Handles sending the advance inactivity warning email if not previously sent,
     * records the warning timestamp in MySQL, and synchronizes to Salesforce.
     * Calculates remaining days until deactivation.
     *
     * @param user         The user entity nearing deactivation
     * @param inactiveDays Current number of inactive days
     */
    private void handleWarning(User user, long inactiveDays) {
        if (Objects.nonNull(user.getInactivityWarningSentAt())) {
            log.debug("Inactivity warning already sent to {} on {}. Skipping duplicate email.",
                    user.getEmail(), user.getInactivityWarningSentAt());
            return;
        }

        long daysRemaining = Math.max(0, INACTIVITY_DAYS - inactiveDays);
        LocalDate deactivationDate = LocalDate.now().plusDays(daysRemaining);

        log.info("Sending inactivity warning email to {} (inactive: {} days, deactivation in {} days on {})",
                user.getEmail(), inactiveDays, daysRemaining, deactivationDate);

        try {
            NotificationRequest warningNotice = new NotificationRequest(
                    user.getEmail(),
                    "Notice: Account Inactivity Warning - Library Management System",
                    null,
                    user.getName(),
                    null,
                    null,
                    EmailTemplateType.INACTIVITY_WARNING,
                    inactiveDays,
                    daysRemaining,
                    deactivationDate
            );
            notificationFactory.get(NotificationTypeEnum.EMAIL).send(warningNotice);
            user.setInactivityWarningSentAt(LocalDateTime.now());
            userRepository.save(user);
            syncUserToSalesforce(user);
            log.info("Persisted and synced inactivity warning timestamp for user {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send inactivity warning to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Synchronizes a user's status and inactivity metadata to Salesforce with failure handling.
     *
     * @param user The user entity to sync
     */
    private void syncUserToSalesforce(User user) {
        if (Objects.nonNull(salesforceSyncDelegate) && Objects.nonNull(user) && Objects.nonNull(user.getUuid())) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(user));
                user.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(user);
            } catch (Exception e) {
                int retryCount = user.getSalesforceRetryCount() + 1;
                user.setSalesforceRetryCount(retryCount);
                user.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(user);
                log.error("Salesforce dual-write failed for user [User ID: {}, UUID: {}, RetryCount: {}]: {}",
                        user.getId(), user.getUuid(), retryCount, e.getMessage());
            }
        }
    }

    /**
     * Resolves the reference baseline timestamp for measuring user inactivity.
     * Prefers {@link User#getLastLoginAt()}; falls back to {@link User#getCreatedAt()}
     * for newly created accounts without a login event, or current timestamp if both are unavailable.
     *
     * @param user The user entity
     * @return The determined baseline {@link LocalDateTime}
     */
    private LocalDateTime determineInactivityBaseline(User user) {
        if (Objects.nonNull(user.getLastLoginAt())) {
            return user.getLastLoginAt();
        }
        if (Objects.nonNull(user.getCreatedAt())) {
            log.debug("User {} has null lastLoginAt, using createdAt as baseline: {}",
                    user.getEmail(), user.getCreatedAt());
            return user.getCreatedAt();
        }
        return LocalDateTime.now();
    }
}
