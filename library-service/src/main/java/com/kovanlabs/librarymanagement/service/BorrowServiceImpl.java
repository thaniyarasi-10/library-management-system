package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
import com.kovanlabs.librarymanagement.mapping.BorrowMapper;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link BorrowService} enforcing membership checks, fine checks,
 * and dual-write synchronization with Salesforce Borrow SObjects.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final UserFineChecker userFineChecker;
    private final MembershipService membershipService;
    private final SalesforceSync salesforceSyncService;
    private final RewardService rewardService;

    @Override
    public BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto) {
        return borrowBook(borrowRequestDto, null);
    }

    @Override
    public BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto, String userIdentifier) {
        if (Objects.isNull(borrowRequestDto)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Borrow request cannot be null");
        }

        User user = resolveUser(borrowRequestDto, userIdentifier);

        if (!membershipService.hasActiveMembership(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only users with an ACTIVE membership can perform borrow operations");
        }

        if (Objects.nonNull(userFineChecker) && userFineChecker.hasPendingFines(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before borrowing books.");
        }

        Book book = resolveBook(borrowRequestDto);

        int totalBookCount = Objects.nonNull(book.getTotalBookCount()) ? book.getTotalBookCount() : 0;
        int borrowedBookCount = Objects.nonNull(book.getBorrowedBookCount()) ? book.getBorrowedBookCount() : 0;

        if (borrowedBookCount >= totalBookCount || totalBookCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No copies available. All copies are currently borrowed.");
        }

        book.setBorrowedBookCount(borrowedBookCount + 1);
        Book updatedBook = bookRepository.save(book);

        syncBookToSalesforce(updatedBook);

        Borrow borrow = BorrowMapper.INSTANCE.mapToEntity(borrowRequestDto, updatedBook, user);
        Borrow savedBorrow = borrowRepository.save(borrow);

        syncBorrowToSalesforce(savedBorrow, "CREATE");

        return BorrowMapper.INSTANCE.mapToResponse(savedBorrow);
    }

    @Override
    public BorrowResponseDto returnBook(Long borrowId) {
        if (Objects.isNull(borrowId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowId is required");
        }

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow record not found"));

        if (BorrowStatus.RETURNED.equals(borrow.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book has already been returned");
        }

        if (Objects.nonNull(userFineChecker) && Objects.nonNull(borrow.getUser()) && userFineChecker.hasPendingFines(borrow.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before returning books.");
        }

        Book book = borrow.getBook();
        if (Objects.nonNull(book)) {
            int borrowedCount = Objects.nonNull(book.getBorrowedBookCount()) ? book.getBorrowedBookCount() : 0;
            book.setBorrowedBookCount(Math.max(0, borrowedCount - 1));
            Book updatedBook = bookRepository.save(book);
            syncBookToSalesforce(updatedBook);
        }

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);
        Borrow updatedBorrow = borrowRepository.save(borrow);

        syncBorrowToSalesforce(updatedBorrow, "UPDATE");
        // Trigger reward points processing
        rewardService.processOnTimeReturnRewards();

        return BorrowMapper.INSTANCE.mapToResponse(updatedBorrow);
    }

    @Override
    public List<BorrowResponseDto> getAllBorrows() {
        return borrowRepository.findAllByOrderByIdDesc().stream()
                .map(BorrowMapper.INSTANCE::mapToResponse)
                .toList();
    }

    @Override
    public List<BorrowResponseDto> getBorrowsByUserId(Long userId) {
        return Objects.isNull(userId)
                ? Collections.emptyList()
                : borrowRepository.findByUser_IdOrderByIdDesc(userId).stream()
                        .map(BorrowMapper.INSTANCE::mapToResponse)
                        .toList();
    }

    @Override
    public List<BorrowResponseDto> getBorrowsByUserEmail(String email) {
        if (Objects.isNull(email)) {
            return Collections.emptyList();
        }
        User user = userProviderRepository.findByProviderId(email)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);
        return (Objects.isNull(user) || Objects.isNull(user.getId()))
                ? Collections.emptyList()
                : getBorrowsByUserId(user.getId());
    }

    private User resolveUser(BorrowRequestDto request, String userIdentifier) {
        if (Objects.nonNull(request.userUuid()) && !request.userUuid().isBlank()) {
            return userRepository.findByUuid(request.userUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found with uuid: " + request.userUuid()));
        }
        if (Objects.nonNull(request.userId())) {
            return userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found with id: " + request.userId()));
        }
        if (Objects.nonNull(userIdentifier) && !userIdentifier.isBlank()) {
            return userProviderRepository.findByProviderId(userIdentifier)
                    .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                    .or(() -> userRepository.findByEmail(userIdentifier))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found for identifier: " + userIdentifier));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier (userId or userUuid) is required");
    }

    private Book resolveBook(BorrowRequestDto request) {
        if (Objects.nonNull(request.bookUuid()) && !request.bookUuid().isBlank()) {
            return bookRepository.findByUuid(request.bookUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Book not found with uuid: " + request.bookUuid()));
        }
        if (Objects.nonNull(request.bookId())) {
            return bookRepository.findById(request.bookId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Book not found with id: " + request.bookId()));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book identifier (bookId or bookUuid) is required");
    }

    private void syncBookToSalesforce(Book book) {
        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBook(BookMapper.INSTANCE.toBookSObject(book));
                book.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                bookRepository.save(book);
            } catch (Exception e) {
                int retryCount = book.getSalesforceRetryCount() + 1;
                book.setSalesforceRetryCount(retryCount);
                book.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                bookRepository.save(book);
                log.error("Salesforce dual-write failed for book count update [Book ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
                        book.getId(), book.getUuid(), retryCount, e.getMessage());
            }
        }
    }

    private void syncBorrowToSalesforce(Borrow borrow, String operation) {
        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(BorrowMapper.INSTANCE.mapToResponse(borrow)));
                borrow.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                borrowRepository.save(borrow);
            } catch (Exception e) {
                int retryCount = borrow.getSalesforceRetryCount() + 1;
                borrow.setSalesforceRetryCount(retryCount);
                borrow.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                borrowRepository.save(borrow);
                log.error("Salesforce dual-write failed for borrow {} [Borrow ID: {}, UUID: {}, Operation: {}, RetryCount: {}]: {}",
                        operation.toLowerCase(), borrow.getId(), borrow.getUuid(), operation, retryCount, e.getMessage());
            }
        }
    }
}
