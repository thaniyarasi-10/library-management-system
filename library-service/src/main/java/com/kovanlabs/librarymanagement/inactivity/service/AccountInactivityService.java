package com.kovanlabs.librarymanagement.inactivity.service;

import com.kovanlabs.librarymanagement.communication.service.InactivityNotificationService;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Service managing user account inactivity tracking, warning notifications,
 * and automatic deactivation directly in MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountInactivityService {

    public static final int INACTIVITY_DAYS = 30;
    public static final int WARNING_DAYS_BEFORE = 7;
    public static final int WARNING_THRESHOLD_DAYS = INACTIVITY_DAYS - WARNING_DAYS_BEFORE;

    private final InactivityNotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Evaluates and processes a user for inactivity warnings or deactivation based on MySQL data.
     *
     * @param user The User entity to evaluate
     */
    @Transactional
    public void processUser(User user) {
        if (Objects.isNull(user) || user.getStatus() != AccountStatus.ACTIVE) {
            return;
        }

        LocalDateTime baseline = determineInactivityBaseline(user);
        long inactiveDays = Duration.between(baseline, LocalDateTime.now()).toDays();

        // Case 1: Inactivity reached or exceeded deadline (>= 30 days) -> DEACTIVATE
        if (inactiveDays >= INACTIVITY_DAYS) {
            deactivateUser(user, inactiveDays);
            return;
        }

        // Case 2: Inactivity reached warning threshold (>= 23 days and < 30 days) -> SEND WARNING
        if (inactiveDays >= WARNING_THRESHOLD_DAYS) {
            handleWarning(user, inactiveDays);
            return;
        }

        // Case 3: User is active (< 23 days). Clear any previous warning timestamp
        if (Objects.nonNull(user.getInactivityWarningSentAt())) {
            user.setInactivityWarningSentAt(null);
            userRepository.save(user);
        }
    }

    private void deactivateUser(User user, long inactiveDays) {
        log.info("User {} (ID: {}) inactive for {} days (threshold: {}). Deactivating account in MySQL.",
                user.getEmail(), user.getId(), inactiveDays, INACTIVITY_DAYS);

        user.setStatus(AccountStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully deactivated user account in MySQL [ID: {}, Email: {}]",
                user.getId(), user.getEmail());
    }

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

        boolean sent = notificationService.sendInactivityWarning(
                user.getEmail(),
                user.getName(),
                inactiveDays,
                daysRemaining,
                deactivationDate
        );

        if (sent) {
            user.setInactivityWarningSentAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Persisted inactivity warning timestamp for user {}", user.getEmail());
        } else {
            log.warn("Failed to send inactivity warning to {}. Will retry on next scheduled run.",
                    user.getEmail());
        }
    }

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
