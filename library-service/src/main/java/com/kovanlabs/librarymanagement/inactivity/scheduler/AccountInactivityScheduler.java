package com.kovanlabs.librarymanagement.inactivity.scheduler;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.inactivity.service.AccountInactivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled job to periodically detect inactive users from MySQL, send warning emails,
 * and automatically deactivate eligible accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountInactivityScheduler {

    public static final int BATCH_SIZE = 50;
    public static final String SCHEDULER_CRON = "0 0 2 * * *";

    private final UserRepository userRepository;
    private final AccountInactivityService inactivityService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Executes the inactivity check process daily at 02:00 AM.
     * Prevents concurrent overlapping executions and processes active users in batches.
     */
    @Scheduled(cron = SCHEDULER_CRON)
    public void processAccountInactivity() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Account inactivity check is already running. Skipping this scheduled execution.");
            return;
        }

        log.info("Starting scheduled account inactivity check (inactivity: {} days, warning: {} days before)",
                AccountInactivityService.INACTIVITY_DAYS, AccountInactivityService.WARNING_DAYS_BEFORE);

        try {
            int page = 0;
            boolean hasMore = true;
            int totalProcessed = 0;

            while (hasMore) {
                Page<User> usersPage = userRepository.findByStatus(AccountStatus.ACTIVE, PageRequest.of(page, BATCH_SIZE));

                if (usersPage.isEmpty()) {
                    log.debug("No active users found on page {}", page);
                    break;
                }

                for (User user : usersPage.getContent()) {
                    try {
                        inactivityService.processUser(user);
                        totalProcessed++;
                    } catch (Exception e) {
                        log.error("Error processing inactivity for user [ID: {}, Email: {}]: {}",
                                user.getId(), user.getEmail(), e.getMessage(), e);
                    }
                }

                hasMore = usersPage.hasNext();
                page++;
            }

            log.info("Completed account inactivity check. Total active users processed: {}", totalProcessed);
        } finally {
            isRunning.set(false);
        }
    }
}
