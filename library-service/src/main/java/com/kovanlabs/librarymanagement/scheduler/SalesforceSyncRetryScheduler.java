package com.kovanlabs.librarymanagement.scheduler;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
import com.kovanlabs.librarymanagement.mapping.BorrowMapper;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Scheduled task that periodically checks for PENDING Salesforce synchronizations
 * across Users, Books, and Borrows, retrying them until max retries is reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesforceSyncRetryScheduler {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;
    private final SalesforceSync salesforceSyncService;
    private final SalesforceConfig salesforceConfig;

    @Scheduled(fixedDelayString = "${salesforce.retry.interval-ms:60000}")
    @Transactional
    public void retryPendingSyncs() {
        retryPendingUsers();
        retryPendingBooks();
        retryPendingBorrows();
    }

    private void retryPendingUsers() {
        List<User> pendingUsers = userRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING);
        if (pendingUsers.isEmpty()) {
            return;
        }

        int maxRetries = salesforceConfig.getMaxRetries();
        for (User user : pendingUsers) {
            int currentRetries = user.getSalesforceRetryCount() + 1;
            user.setSalesforceRetryCount(currentRetries);

            try {
                if (Objects.nonNull(salesforceSyncService)) {
                    salesforceSyncService.syncContact(UserMapper.INSTANCE.toContactSObject(user));
                }
                user.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(user);
                log.info("Successfully retried Salesforce sync for User [User ID: {}, UUID: {}, RetryCount: {}]",
                        user.getId(), user.getUuid(), currentRetries);
            } catch (Exception e) {
                if (currentRetries >= maxRetries) {
                    user.setSalesforceSyncStatus(SalesforceSyncStatus.FAILED);
                    userRepository.save(user);
                    log.error("Salesforce sync permanently FAILED after reaching max retries for User [User ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            user.getId(), user.getUuid(), currentRetries, maxRetries, e.getMessage());
                } else {
                    userRepository.save(user);
                    log.warn("Salesforce sync retry failed for User [User ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            user.getId(), user.getUuid(), currentRetries, maxRetries, e.getMessage());
                }
            }
        }
    }

    private void retryPendingBooks() {
        List<Book> pendingBooks = bookRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING);
        if (pendingBooks.isEmpty()) {
            return;
        }

        int maxRetries = salesforceConfig.getMaxRetries();
        for (Book book : pendingBooks) {
            int currentRetries = book.getSalesforceRetryCount() + 1;
            book.setSalesforceRetryCount(currentRetries);

            try {
                if (Objects.nonNull(salesforceSyncService)) {
                    salesforceSyncService.syncBook(BookMapper.INSTANCE.toBookSObject(book));
                }
                book.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                bookRepository.save(book);
                log.info("Successfully retried Salesforce sync for Book [Book ID: {}, UUID: {}, RetryCount: {}]",
                        book.getId(), book.getUuid(), currentRetries);
            } catch (Exception e) {
                if (currentRetries >= maxRetries) {
                    book.setSalesforceSyncStatus(SalesforceSyncStatus.FAILED);
                    bookRepository.save(book);
                    log.error(
                            "Salesforce sync permanently FAILED after reaching max retries for Book [Book ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            book.getId(), book.getUuid(), currentRetries, maxRetries, e.getMessage());
                } else {
                    bookRepository.save(book);
                    log.warn(
                            "Salesforce sync retry failed for Book [Book ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            book.getId(), book.getUuid(), currentRetries, maxRetries, e.getMessage());
                }
            }
        }
    }

    private void retryPendingBorrows() {
        List<Borrow> pendingBorrows = borrowRepository.findBySalesforceSyncStatus(SalesforceSyncStatus.PENDING);
        if (pendingBorrows.isEmpty()) {
            return;
        }

        int maxRetries = salesforceConfig.getMaxRetries();
        for (Borrow borrow : pendingBorrows) {
            int currentRetries = borrow.getSalesforceRetryCount() + 1;
            borrow.setSalesforceRetryCount(currentRetries);

            try {
                if (Objects.nonNull(salesforceSyncService)) {
                    salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(borrow));
                }
                borrow.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                borrowRepository.save(borrow);
                log.info("Successfully retried Salesforce sync for Borrow [Borrow ID: {}, UUID: {}, RetryCount: {}]",
                        borrow.getId(), borrow.getUuid(), currentRetries);
            } catch (Exception e) {
                if (currentRetries >= maxRetries) {
                    borrow.setSalesforceSyncStatus(SalesforceSyncStatus.FAILED);
                    borrowRepository.save(borrow);
                    log.error(
                            "Salesforce sync permanently FAILED after reaching max retries for Borrow [Borrow ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            borrow.getId(), borrow.getUuid(), currentRetries, maxRetries, e.getMessage());
                } else {
                    borrowRepository.save(borrow);
                    log.warn(
                            "Salesforce sync retry failed for Borrow [Borrow ID: {}, UUID: {}, Operation: RETRY, RetryCount: {}, MaxRetries: {}]: {}",
                            borrow.getId(), borrow.getUuid(), currentRetries, maxRetries, e.getMessage());
                }
            }
        }
    }
}
