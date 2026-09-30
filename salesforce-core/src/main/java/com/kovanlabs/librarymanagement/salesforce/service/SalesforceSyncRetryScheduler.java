package com.kovanlabs.librarymanagement.salesforce.service;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.config.SalesforceConfig;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.SObjectAttributes;
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
                    salesforceSyncService.syncContact(toContactSObject(user));
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
                    salesforceSyncService.syncBook(toBookSObject(book));
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
                    salesforceSyncService.syncBorrow(toBorrowSObject(borrow));
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

    private ContactSObject toContactSObject(User user) {
        if (Objects.isNull(user)) {
            return null;
        }
        return ContactSObject.builder()
                .attributes(SObjectAttributes.builder().type(SObject.CONTACT.getObjectName()).build())
                .externalUserUuid(Objects.nonNull(user.getUuid()) ? user.getUuid().toString() : null)
                .legacyUserId(user.getId())
                .lastName(Objects.nonNull(user.getName()) && !user.getName().isBlank() ? user.getName() : "User")
                .email(user.getEmail())
                .role(Objects.nonNull(user.getRole()) ? user.getRole().name() : null)
                .build();
    }

    private BookSObject toBookSObject(Book book) {
        if (Objects.isNull(book)) {
            return null;
        }
        return BookSObject.builder()
                .attributes(SObjectAttributes.builder().type(SObject.BOOK.getObjectName()).build())
                .externalBookUuid(Objects.nonNull(book.getUuid()) ? book.getUuid().toString() : null)
                .name(Objects.nonNull(book.getTitle()) && !book.getTitle().isBlank() ? book.getTitle() : "Untitled")
                .title(Objects.nonNull(book.getTitle()) && !book.getTitle().isBlank() ? book.getTitle() : "Untitled")
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .coverImageUrl(book.getCoverImageUrl())
                .build();
    }

    private BorrowSObject toBorrowSObject(Borrow borrow) {
        if (Objects.isNull(borrow)) {
            return null;
        }
        ContactSObject contact = null;
        if (Objects.nonNull(borrow.getUser())) {
            contact = ContactSObject.builder()
                    .externalUserUuid(Objects.nonNull(borrow.getUser().getUuid()) ? borrow.getUser().getUuid().toString() : null)
                    .build();
        }
        BookSObject book = null;
        if (Objects.nonNull(borrow.getBook())) {
            book = BookSObject.builder()
                    .externalBookUuid(Objects.nonNull(borrow.getBook().getUuid()) ? borrow.getBook().getUuid().toString() : null)
                    .build();
        }
        return BorrowSObject.builder()
                .attributes(SObjectAttributes.builder().type(SObject.BORROW.getObjectName()).build())
                .externalBorrowUuid(Objects.nonNull(borrow.getUuid()) ? borrow.getUuid().toString() : null)
                .borrowDate(Objects.nonNull(borrow.getBorrowDate()) ? borrow.getBorrowDate().toString() : null)
                .dueDate(Objects.nonNull(borrow.getDueDate()) ? borrow.getDueDate().toString() : null)
                .returnDate(Objects.nonNull(borrow.getReturnedDate()) ? borrow.getReturnedDate().toString() : null)
                .borrowStatus(Objects.nonNull(borrow.getStatus()) ? borrow.getStatus().name() : null)
                .contact(contact)
                .book(book)
                .build();
    }
}
