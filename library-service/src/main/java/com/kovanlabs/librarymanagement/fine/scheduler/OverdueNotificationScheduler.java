package com.kovanlabs.librarymanagement.fine.scheduler;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.aws.sqs.producer.SqsNotificationProducer;
import com.kovanlabs.librarymanagement.database.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueNotificationScheduler {

    private final BorrowRepository borrowRepository;
    private final SqsNotificationProducer sqsNotificationProducer;
    private final FineService fineService;

    @Scheduled(cron = "${notification.scheduling.cron:0 0/5 * * * *}")
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
                .filter(b -> b.getUser() != null && b.getUser().getEmail() != null)
                .collect(Collectors.groupingBy(Borrow::getUser));

        for (Map.Entry<User, List<Borrow>> entry : userBorrowsMap.entrySet()) {

            User user = entry.getKey();
            List<Borrow> borrows = entry.getValue();

            List<OverdueBookDto> overdueBookDtos = borrows.stream()
                    .map(b -> {
                        if (b.getStatus() != BorrowStatus.OVERDUE) {
                            b.setStatus(BorrowStatus.OVERDUE);
                            borrowRepository.save(b);
                        }

                        FineResult result = fineService.calculateFine(b);
                        fineService.processFineForBorrow(b);

                        return new OverdueBookDto(
                                b.getBook() != null ? b.getBook().getTitle() : "Unknown Title",
                                b.getBook() != null ? b.getBook().getAuthor() : "Unknown Author",
                                b.getDueDate(),
                                result.daysOverdue(),
                                result.fine()
                        );
                    })
                    .toList();

            BigDecimal userTotalPendingFine = fineService.calculateTotalPendingFineForUser(user.getUuid());
            double totalFine = userTotalPendingFine.doubleValue();

            NotificationRequest notificationRequest = new NotificationRequest(
                    user.getEmail(),
                    "Overdue Books Notice",
                    "Please return your overdue books to avoid further fines.",
                    user.getName() != null ? user.getName() : user.getEmail(),
                    overdueBookDtos,
                    totalFine
            );

            try {
                sqsNotificationProducer.send(notificationRequest);
                log.info("Successfully sent overdue notification request to SQS producer: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send scheduled overdue notification to user {}: {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }
}



