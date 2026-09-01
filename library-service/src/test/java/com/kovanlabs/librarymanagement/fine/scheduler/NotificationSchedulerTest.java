package com.kovanlabs.librarymanagement.fine.scheduler;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.aws.sqs.producer.SqsNotificationProducer;
import com.kovanlabs.librarymanagement.database.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationSchedulerTest {

        private BorrowRepository borrowRepository;
        private SqsNotificationProducer sqsNotificationProducer;
        private FineService fineService;
        private NotificationScheduler scheduler;
        private UUID userUuid;
        private UUID bookUuid;
        private UUID borrowUuid;

        @BeforeEach
        void setUp() {
                borrowRepository = mock(BorrowRepository.class);
                sqsNotificationProducer = mock(SqsNotificationProducer.class);
                fineService = mock(FineService.class);

                scheduler = new NotificationScheduler(borrowRepository, sqsNotificationProducer, fineService);

                userUuid = UUID.randomUUID();
                bookUuid = UUID.randomUUID();
                borrowUuid = UUID.randomUUID();
        }

        @Test
        @DisplayName("Should do nothing when no overdue borrows exist")
        void testSendOverdueNotifications_NoOverdue() {
                when(borrowRepository.findByReturnedDateIsNullAndDueDateBefore(any(LocalDate.class)))
                                .thenReturn(Collections.emptyList());

                scheduler.sendOverdueNotifications();

                verify(sqsNotificationProducer, never()).send(any());
                verify(borrowRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should send SQS notification, process fine, and update status to OVERDUE for overdue borrows")
        void testSendOverdueNotifications_WithOverdueBorrows() {
                User user = User.builder()
                                .uuid(userUuid)
                                .id(1L)
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .build();

                Book book = Book.builder()
                                .uuid(bookUuid)
                                .id(101L)
                                .title("Clean Code")
                                .author("Robert C. Martin")
                                .build();

                Borrow borrow = Borrow.builder()
                                .uuid(borrowUuid)
                                .id(1L)
                                .user(user)
                                .book(book)
                                .borrowDate(LocalDate.now().minusDays(20))
                                .dueDate(LocalDate.now().minusDays(6))
                                .status(BorrowStatus.BORROWED)
                                .build();

                when(borrowRepository.findByReturnedDateIsNullAndDueDateBefore(any(LocalDate.class)))
                                .thenReturn(List.of(borrow));

                when(fineService.calculateFine(borrow)).thenReturn(new FineResult(borrow, 6, 30.0));
                when(fineService.calculateTotalPendingFineForUser(userUuid)).thenReturn(BigDecimal.valueOf(30.0));

                scheduler.sendOverdueNotifications();

                assertEquals(BorrowStatus.OVERDUE, borrow.getStatus());
                verify(borrowRepository, times(1)).saveAll(any());
                verify(fineService, times(1)).processFineForBorrow(borrow);
                verify(fineService, times(1)).calculateTotalPendingFineForUser(userUuid);

                ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
                verify(sqsNotificationProducer, times(1)).send(captor.capture());

                NotificationRequest request = captor.getValue();
                assertEquals("john.doe@example.com", request.recipient());
                assertEquals("Overdue Books Notice", request.subject());
                assertEquals("John Doe", request.userName());
                assertNotNull(request.overdueBooks());
                assertEquals(1, request.overdueBooks().size());
                assertEquals("Clean Code", request.overdueBooks().get(0).title());
                assertEquals(30.0, request.totalFine());
        }
}
