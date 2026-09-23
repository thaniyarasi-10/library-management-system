package com.kovanlabs.librarymanagement.scheduler;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSyncService;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.service.SalesforceUserSyncDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesforceSyncRetrySchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BorrowRepository borrowRepository;

    @Mock
    private SalesforceSyncService salesforceSyncService;

    @Mock
    private SalesforceConfig salesforceConfig;

    @InjectMocks
    private SalesforceSyncRetryScheduler scheduler;

    private User pendingUser;
    private Book pendingBook;
    private Borrow pendingBorrow;

    @BeforeEach
    void setUp() {
        lenient().when(salesforceConfig.getMaxRetries()).thenReturn(3);

        pendingUser = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .salesforceSyncStatus(SalesforceSyncStatus.PENDING)
                .salesforceRetryCount(0)
                .build();

        pendingBook = Book.builder()
                .id(10L)
                .uuid(UUID.randomUUID())
                .title("Clean Code")
                .author("Uncle Bob")
                .isbn("1234567890")
                .salesforceSyncStatus(SalesforceSyncStatus.PENDING)
                .salesforceRetryCount(0)
                .build();

        pendingBorrow = Borrow.builder()
                .id(100L)
                .uuid(UUID.randomUUID())
                .user(pendingUser)
                .book(pendingBook)
                .salesforceSyncStatus(SalesforceSyncStatus.PENDING)
                .salesforceRetryCount(0)
                .build();
    }

    @Test
    @DisplayName("retryPendingSyncs with no pending records does nothing")
    void retryPendingSyncs_whenNoPending_doesNothing() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());

        scheduler.retryPendingSyncs();

        verify(userRepository, never()).save(any());
        verify(bookRepository, never()).save(any());
        verify(borrowRepository, never()).save(any());
    }

    @Test
    @DisplayName("retryPendingSyncs should successfully sync User and mark SUCCESS")
    void retryPendingSyncs_userSuccess_marksSuccess() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingUser));
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());

        scheduler.retryPendingSyncs();

        verify(salesforceSyncService).syncUser(any(UserResponse.class));
        verify(userRepository).save(pendingUser);
        assertEquals(SalesforceSyncStatus.SUCCESS, pendingUser.getSalesforceSyncStatus());
        assertEquals(1, pendingUser.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when User retry fails below maxRetries, stays PENDING")
    void retryPendingSyncs_userFailureBelowMax_staysPending() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingUser));
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("SF timeout")).when(salesforceSyncService).syncUser(any());

        scheduler.retryPendingSyncs();

        verify(userRepository).save(pendingUser);
        assertEquals(SalesforceSyncStatus.PENDING, pendingUser.getSalesforceSyncStatus());
        assertEquals(1, pendingUser.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when User reaches maxRetries, marks FAILED")
    void retryPendingSyncs_userFailureAtMax_marksFailed() {
        pendingUser.setSalesforceRetryCount(2);
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingUser));
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("SF persistent error")).when(salesforceSyncService).syncUser(any());

        scheduler.retryPendingSyncs();

        verify(userRepository).save(pendingUser);
        assertEquals(SalesforceSyncStatus.FAILED, pendingUser.getSalesforceSyncStatus());
        assertEquals(3, pendingUser.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs should successfully sync Book and mark SUCCESS")
    void retryPendingSyncs_bookSuccess_marksSuccess() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBook));
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());

        scheduler.retryPendingSyncs();

        verify(salesforceSyncService).syncBook(any(BookSObject.class));
        verify(bookRepository).save(pendingBook);
        assertEquals(SalesforceSyncStatus.SUCCESS, pendingBook.getSalesforceSyncStatus());
        assertEquals(1, pendingBook.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when Book retry fails below maxRetries, stays PENDING")
    void retryPendingSyncs_bookFailureBelowMax_staysPending() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBook));
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Book SF timeout")).when(salesforceSyncService).syncBook(any());

        scheduler.retryPendingSyncs();

        verify(bookRepository).save(pendingBook);
        assertEquals(SalesforceSyncStatus.PENDING, pendingBook.getSalesforceSyncStatus());
        assertEquals(1, pendingBook.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when Book reaches maxRetries, marks FAILED")
    void retryPendingSyncs_bookFailureAtMax_marksFailed() {
        pendingBook.setSalesforceRetryCount(2);
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBook));
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Book SF persistent error")).when(salesforceSyncService).syncBook(any());

        scheduler.retryPendingSyncs();

        verify(bookRepository).save(pendingBook);
        assertEquals(SalesforceSyncStatus.FAILED, pendingBook.getSalesforceSyncStatus());
        assertEquals(3, pendingBook.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs should successfully sync Borrow and mark SUCCESS")
    void retryPendingSyncs_borrowSuccess_marksSuccess() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBorrow));

        scheduler.retryPendingSyncs();

        verify(salesforceSyncService).syncBorrow(any(BorrowSObject.class));
        verify(borrowRepository).save(pendingBorrow);
        assertEquals(SalesforceSyncStatus.SUCCESS, pendingBorrow.getSalesforceSyncStatus());
        assertEquals(1, pendingBorrow.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when Borrow retry fails below maxRetries, stays PENDING")
    void retryPendingSyncs_borrowFailureBelowMax_staysPending() {
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBorrow));
        doThrow(new RuntimeException("Borrow SF timeout")).when(salesforceSyncService).syncBorrow(any());

        scheduler.retryPendingSyncs();

        verify(borrowRepository).save(pendingBorrow);
        assertEquals(SalesforceSyncStatus.PENDING, pendingBorrow.getSalesforceSyncStatus());
        assertEquals(1, pendingBorrow.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("retryPendingSyncs when Borrow reaches maxRetries, marks FAILED")
    void retryPendingSyncs_borrowFailureAtMax_marksFailed() {
        pendingBorrow.setSalesforceRetryCount(2);
        when(userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(Collections.emptyList());
        when(borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING)).thenReturn(List.of(pendingBorrow));
        doThrow(new RuntimeException("Borrow SF persistent error")).when(salesforceSyncService).syncBorrow(any());

        scheduler.retryPendingSyncs();

        verify(borrowRepository).save(pendingBorrow);
        assertEquals(SalesforceSyncStatus.FAILED, pendingBorrow.getSalesforceSyncStatus());
        assertEquals(3, pendingBorrow.getSalesforceRetryCount());
    }
}
