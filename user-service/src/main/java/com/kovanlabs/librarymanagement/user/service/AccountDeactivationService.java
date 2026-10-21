package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kovanlabs.librarymanagement.common.event.UserPermanentlyDeactivatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeactivationService {

    public static final int REACTIVATION_DEADLINE_DAYS = 10;

    private final UserRepository userRepository;
    private final SalesforceSync salesforceSyncDelegate;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /**
     * Resolves the effective timestamp when an account deactivation occurred.
     * Prefers {@link User#getDeactivatedAt()}; falls back to {@link User#getUpdatedAt()}
     * if {@code deactivatedAt} is null.
     *
     * @param user The user entity
     * @return The effective deactivation timestamp, or null if both are null
     */
    public LocalDateTime getEffectiveDeactivationDate(User user) {
        if (Objects.isNull(user)) {
            return null;
        }
        return Objects.nonNull(user.getDeactivatedAt()) ? user.getDeactivatedAt() : user.getUpdatedAt();
    }

    /**
     * Calculates the exact deadline timestamp for requesting reactivation.
     * Calculated as {@code effectiveDeactivationDate + 10 days}.
     *
     * @param user The user entity
     * @return The reactivation deadline, or null if deactivation date is unknown
     */
    public LocalDateTime calculateReactivationDeadline(User user) {
        LocalDateTime effectiveDate = getEffectiveDeactivationDate(user);
        return Objects.nonNull(effectiveDate) ? effectiveDate.plusDays(REACTIVATION_DEADLINE_DAYS) : null;
    }

    /**
     * Determines whether the 10-day reactivation window has expired as of current time.
     * Users may submit a request up to and including {@code deactivated_at + 10 days}.
     * Missing deactivation timestamp is safely treated as not expired to avoid false rejections.
     *
     * @param user The user entity
     * @return {@code true} if expired, {@code false} if within permitted window or timestamp missing
     */
    public boolean isReactivationWindowExpired(User user) {
        return isReactivationWindowExpired(user, LocalDateTime.now());
    }

    /**
     * Determines whether the 10-day reactivation window has expired as of a specific reference timestamp.
     *
     * @param user The user entity
     * @param now  The reference timestamp to compare against
     * @return {@code true} if expired, {@code false} if within permitted window or timestamp missing
     */
    public boolean isReactivationWindowExpired(User user, LocalDateTime now) {
        if (Objects.isNull(user)) {
            return false;
        }
        LocalDateTime deadline = calculateReactivationDeadline(user);
        if (Objects.isNull(deadline)) {
            return false;
        }
        return now.isAfter(deadline);
    }

    /**
     * Determines whether an account is eligible for permanent deactivation.
     * An account is eligible if and only if:
     * <ol>
     *     <li>Account status is {@link AccountStatus#DEACTIVATED}</li>
     *     <li>Reactivation request status is NOT {@link ReactivationRequestStatus#PENDING}</li>
     *     <li>The 10-day reactivation window has expired</li>
     * </ol>
     * Pending requests strictly protect the account from permanent deactivation.
     *
     * @param user The user entity to evaluate
     * @return {@code true} if eligible for permanent deactivation
     */
    public boolean isEligibleForPermanentDeactivation(User user) {
        return isEligibleForPermanentDeactivation(user, LocalDateTime.now());
    }

    /**
     * Determines whether an account is eligible for permanent deactivation as of a specific reference timestamp.
     *
     * @param user The user entity to evaluate
     * @param now  The reference timestamp
     * @return {@code true} if eligible for permanent deactivation
     */
    public boolean isEligibleForPermanentDeactivation(User user, LocalDateTime now) {
        if (Objects.isNull(user) || user.getStatus() != AccountStatus.DEACTIVATED) {
            return false;
        }
        if (user.getReactivationRequestStatus() == ReactivationRequestStatus.PENDING) {
            return false;
        }
        return isReactivationWindowExpired(user, now);
    }

    /**
     * Checks if a deactivated user account has exceeded the 10-day reactivation restriction window.
     * If exceeded and no pending request exists, updates the status to {@link AccountStatus#PERMANENTLY_DEACTIVATED},
     * clears any pending request status, persists the update to MySQL, and synchronizes to Salesforce.
     *
     * @param user The user entity to verify
     * @return The updated user entity
     */
    @Transactional
    public User checkAndTransitionExpiredDeactivation(User user) {
        if (Objects.nonNull(user) && user.getStatus() == AccountStatus.DEACTIVATED) {
            if (user.getReactivationRequestStatus() == ReactivationRequestStatus.PENDING) {
                return user;
            }
            if (isReactivationWindowExpired(user)) {
                user.setStatus(AccountStatus.PERMANENTLY_DEACTIVATED);
                user.setReactivationRequestStatus(null);
                User savedUser = userRepository.save(user);
                syncUserToSalesforce(savedUser);
                notifyPermanentDeactivation(savedUser.getUuid());
                log.info("Account for user ID {} ({}) exceeded {}-day reactivation window. Automatically transitioned to PERMANENTLY_DEACTIVATED.",
                        savedUser.getId(), savedUser.getEmail(), REACTIVATION_DEADLINE_DAYS);
                return savedUser;
            }
        }
        return user;
    }

    /**
     * Permanently deactivates an individual user, persisting to MySQL and synchronizing to Salesforce.
     * Guards against race conditions by re-verifying the fresh entity from the repository.
     *
     * @param user The user entity to permanently deactivate
     */
    @Transactional
    public void permanentlyDeactivateUser(User user) {
        if (Objects.isNull(user) || Objects.isNull(user.getId())) {
            return;
        }
        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (!isEligibleForPermanentDeactivation(freshUser)) {
            log.info("User ID {} ({}) is not eligible for permanent deactivation (status: {}, requestStatus: {}); skipping.",
                    freshUser.getId(), freshUser.getEmail(), freshUser.getStatus(), freshUser.getReactivationRequestStatus());
            return;
        }

        log.info("User {} (ID: {}) exceeded {}-day reactivation window. Setting PERMANENTLY_DEACTIVATED.",
                freshUser.getEmail(), freshUser.getId(), REACTIVATION_DEADLINE_DAYS);
        freshUser.setStatus(AccountStatus.PERMANENTLY_DEACTIVATED);
        freshUser.setReactivationRequestStatus(null);
        User savedUser = userRepository.save(freshUser);
        syncUserToSalesforce(savedUser);
        notifyPermanentDeactivation(savedUser.getUuid());
        log.info("Successfully updated user {} to PERMANENTLY_DEACTIVATED in MySQL and initiated Salesforce sync",
                savedUser.getEmail());
    }

    public void notifyPermanentDeactivation(String userUuid) {
        if (Objects.nonNull(eventPublisher) && Objects.nonNull(userUuid)) {
            eventPublisher.publishEvent(new UserPermanentlyDeactivatedEvent(userUuid));
        }
    }

    /**
     * Identifies accounts that have been deactivated for more than 10 days without a pending reactivation request,
     * updates their status to PERMANENTLY_DEACTIVATED in MySQL, and synchronizes the updated status to Salesforce.
     *
     * @return The number of accounts permanently deactivated
     */
    @Transactional
    public int processExpiredDeactivations() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(REACTIVATION_DEADLINE_DAYS);
        List<User> expiredUsers = userRepository.findUsersEligibleForPermanentDeactivation(AccountStatus.DEACTIVATED, cutoff);
        if (Objects.nonNull(expiredUsers) && !expiredUsers.isEmpty()) {
            log.info("Processing {} expired deactivated user(s) for permanent deactivation", expiredUsers.size());
            int count = 0;
            for (User user : expiredUsers) {
                User freshUser = userRepository.findById(user.getId()).orElse(user);
                if (isEligibleForPermanentDeactivation(freshUser)) {
                    permanentlyDeactivateUser(freshUser);
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    /**
     * Synchronizes a user's status to Salesforce with failure retry tracking.
     *
     * @param user The user entity to sync
     */
    public void syncUserToSalesforce(User user) {
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
}
