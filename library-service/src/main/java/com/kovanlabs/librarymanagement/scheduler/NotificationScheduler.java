package com.kovanlabs.librarymanagement.scheduler;

import com.kovanlabs.librarymanagement.aws.sqs.producer.SqsNotificationProducer;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.dto.FineResult;
import com.kovanlabs.librarymanagement.service.AccountInactivityService;
import com.kovanlabs.librarymanagement.service.FineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Consolidated notification scheduler managing periodic system background jobs:
 * <ul>
 *     <li><b>Overdue Book & Fine Notifications:</b> Runs daily at midnight to scan overdue borrows, calculate pending fines, and publish notification requests to Amazon SQS.</li>
 *     <li><b>Account Inactivity Lifecycle:</b> Runs daily at 02:00 AM to process inactivity warnings and deactivations using optimized database queries.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final BorrowRepository borrowRepository;
    private final SqsNotificationProducer sqsNotificationProducer;
    private final FineService fineService;
    private final AccountInactivityService inactivityService;

    /**
     * Executes the overdue borrow processing job daily at midnight.
     * Updates borrow record statuses, calculates accrued overdue fines, and enqueues
     * notification requests to SQS for asynchronous email delivery.
     */
    @Scheduled(cron = "${notification.scheduling.cron:0 0 0 * * *}")
    @Transactional
    public void sendOverdueNotifications() {
        log.info("Executing scheduled task at midnight to process overdue borrow records and send notifications");

        LocalDate today = LocalDate.now();
        List<Borrow> overdueBorrows = borrowRepository.findByReturnedDateIsNullAndDueDateBefore(today);

        if (overdueBorrows.isEmpty()) {
            log.info("No overdue borrows found today ({})", today);
            return;
        }

        log.info("Found {} overdue borrow records to process", overdueBorrows.size());

        Map<User, List<Borrow>> userBorrowsMap = overdueBorrows.stream()
                .filter(b -> Objects.nonNull(b.getUser()) && Objects.nonNull(b.getUser().getEmail()))
                .collect(Collectors.groupingBy(Borrow::getUser));

        for (Map.Entry<User, List<Borrow>> entry : userBorrowsMap.entrySet()) {
            User user = entry.getKey();
            List<Borrow> borrows = entry.getValue();

            List<OverdueBookDto> overdueBookDtos = borrows.stream()
                    .map(b -> {
                        if (b.getStatus() != BorrowStatus.OVERDUE) {
                            b.setStatus(BorrowStatus.OVERDUE);
                        }

                        FineResult result = fineService.calculateFine(b);
                        fineService.processFineForBorrow(b);

                        return new OverdueBookDto(
                                Objects.nonNull(b.getBook()) ? b.getBook().getTitle() : "Unknown Title",
                                Objects.nonNull(b.getBook()) ? b.getBook().getAuthor() : "Unknown Author",
                                b.getDueDate(),
                                result.daysOverdue(),
                                result.fine()
                        );
                    })
                    .toList();
            borrowRepository.saveAll(borrows);

            BigDecimal userTotalPendingFine = fineService.calculateTotalPendingFineForUser(user.getUuid());
            double totalFine = userTotalPendingFine.doubleValue();

            NotificationRequest notificationRequest = new NotificationRequest(
                    user.getEmail(),
                    "Overdue Books Notice",
                    "Please return your overdue books to avoid further fines.",
                    Objects.nonNull(user.getName()) ? user.getName() : user.getEmail(),
                    overdueBookDtos,
                    totalFine,
                    EmailTemplateType.OVERDUE_FINE,
                    null,
                    null,
                    null
            );

            try {
                sqsNotificationProducer.send(notificationRequest);
                log.info("Successfully sent overdue notification request to SQS producer: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send scheduled overdue notification to user {}: {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }

    /**
     * Executes the account inactivity evaluation job daily at 02:00 AM.
     * Evaluates inactive accounts via targeted database queries and dispatches
     * advance warnings (at &ge; 23 days) and deactivation notices (at &ge; 30 days)
     * directly via the email notification service.
     */
    @Scheduled(cron = "${inactivity.scheduling.cron:0 0/2 * * * *}")
    public void processAccountInactivityNotifications() {
        log.info("Executing scheduled task at 02:00 AM to process account inactivity warnings and deactivations");
        try {
            inactivityService.processInactiveAccounts();
            log.info("Successfully finished scheduled account inactivity check");
        } catch (Exception e) {
            log.error("Error occurred during scheduled account inactivity processing: {}", e.getMessage(), e);
        }
    }
}
