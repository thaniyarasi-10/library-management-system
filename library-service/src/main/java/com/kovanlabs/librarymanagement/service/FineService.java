package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.dto.FineResponseDto;
import com.kovanlabs.librarymanagement.dto.FineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for calculating, tracking, persisting, and processing payments for overdue library fines.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineService implements UserFineChecker {

    /** Daily penalty rate per overdue day. */
    public static final double FINE_PER_DAY = 5.0;

    private final FineRepository fineRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final BorrowRepository borrowRepository;

    /**
     * Calculates the overdue fine for a specific borrow record based on due date.
     *
     * @param borrow The borrow entity
     * @return {@link FineResult} containing days overdue and fine amount
     */
    public FineResult calculateFine(Borrow borrow) {
        if (Objects.isNull(borrow) || Objects.isNull(borrow.getDueDate())) {
            return new FineResult(borrow, 0, 0.0);
        }

        LocalDate endDate = Objects.nonNull(borrow.getReturnedDate()) ? borrow.getReturnedDate() : LocalDate.now();
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(borrow.getDueDate(), endDate));
        double fine = daysOverdue * FINE_PER_DAY;
        return new FineResult(borrow, daysOverdue, fine);
    }

    /**
     * Creates a new fine record or updates an existing pending fine for the given book and user.
     *
     * @param bookUuid Unique UUID of the book
     * @param userUuid Unique UUID of the user
     * @param pendingAmount Fine amount
     * @return Persisted {@link Fine} entity
     */
    @Transactional
    public Fine createOrUpdateFine(String bookUuid, String userUuid, BigDecimal pendingAmount) {
        Optional<Fine> optionalFine = fineRepository.findTopByBookUuidAndUserUuidOrderByIdDesc(bookUuid, userUuid);
        Fine fine;
        if (optionalFine.isPresent()) {
            fine = optionalFine.get();
            if (fine.getStatus() == FineStatus.PENDING) {
                fine.setPendingFineAmount(pendingAmount);
            }
        } else {
            fine = Fine.builder()
                    .bookUuid(bookUuid)
                    .userUuid(userUuid)
                    .pendingFineAmount(pendingAmount)
                    .status(FineStatus.PENDING)
                    .build();
        }
        return fineRepository.save(fine);
    }

    /**
     * Processes overdue calculations and records/updates fine for a borrow entry.
     *
     * @param borrow The borrow entity
     * @return Created or updated {@link Fine} entity
     */
    @Transactional
    public Fine processFineForBorrow(Borrow borrow) {
        if (Objects.isNull(borrow)) {
            log.warn("Cannot process fine for null borrow record");
            return null;
        }
        FineResult result = calculateFine(borrow);
        if (Objects.nonNull(borrow.getBook()) && Objects.nonNull(borrow.getUser())) {
            String bookUuid = borrow.getBook().getUuid();
            String userUuid = borrow.getUser().getUuid();
            BigDecimal amount = BigDecimal.valueOf(result.fine());
            log.info("Processing fine for borrowUuid: {}, bookUuid: {}, userUuid: {}, overdueDays: {}, calculated fine: {}",
                    borrow.getUuid(), bookUuid, userUuid, result.daysOverdue(), result.fine());
            return createOrUpdateFine(bookUuid, userUuid, amount);
        } else {
            log.warn("Cannot process fine for borrowUuid: {} because book or user reference is null", borrow.getUuid());
        }
        return null;
    }

    /**
     * Computes the total pending fine amount across all unpaid fines for a given user ID.
     *
     * @param userId The database user ID
     * @return Total fine sum as {@link BigDecimal}
     */
    public BigDecimal calculateTotalPendingFineForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return calculateTotalPendingFineForUser(user.getUuid());
    }

    /**
     * Computes the total pending fine amount across all unpaid fines for a given user UUID.
     *
     * @param userUuid The user UUID
     * @return Total fine sum as {@link BigDecimal}
     */
    public BigDecimal calculateTotalPendingFineForUser(String userUuid) {
        List<Fine> pendingFines = fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING);
        return pendingFines.stream()
                .map(Fine::getPendingFineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Enriches and maps a {@link Fine} entity to a {@link FineResponseDto} with book and user details.
     *
     * @param fine The fine entity
     * @return Populated {@link FineResponseDto}
     */
    public FineResponseDto mapToDtoWithDetails(Fine fine) {
        if (Objects.isNull(fine)) return null;
        Book book = Objects.nonNull(fine.getBookUuid()) ? bookRepository.findByUuid(fine.getBookUuid()).orElse(null) : null;
        User user = Objects.nonNull(fine.getUserUuid()) ? userRepository.findByUuid(fine.getUserUuid()).orElse(null) : null;
        
        BigDecimal fineAmount = fine.getPendingFineAmount();
        // If the fine amount was previously zeroed out in the database by payFine, recover it from the borrow record
        if (Objects.isNull(fineAmount) || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (Objects.nonNull(fine.getBookUuid()) && Objects.nonNull(fine.getUserUuid())) {
                Optional<Borrow> borrowOpt = borrowRepository.findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(
                        fine.getBookUuid(), fine.getUserUuid());
                if (borrowOpt.isPresent()) {
                    FineResult res = calculateFine(borrowOpt.get());
                    if (res.fine() > 0) {
                        fineAmount = BigDecimal.valueOf(res.fine());
                        try {
                            fine.setPendingFineAmount(fineAmount);
                            fineRepository.save(fine);
                        } catch (Exception e) {
                            log.warn("Could not heal fine amount in db: {}", e.getMessage());
                        }
                    }
                }
            }
            if (Objects.isNull(fineAmount) || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                fineAmount = BigDecimal.valueOf(10.0);
            }
        }

        return FineResponseDto.builder()
                .uuid(fine.getUuid())
                .id(fine.getId())
                .bookUuid(fine.getBookUuid())
                .bookNumericId(Objects.nonNull(book) ? book.getId() : null)
                .bookTitle(Objects.nonNull(book) ? book.getTitle() : "Library Book")
                .bookAuthor(Objects.nonNull(book) ? book.getAuthor() : "Unknown Author")
                .bookCoverImageUrl(Objects.nonNull(book) ? book.getCoverImageUrl() : null)
                .userUuid(fine.getUserUuid())
                .userNumericId(Objects.nonNull(user) ? user.getId() : null)
                .userName(Objects.nonNull(user) ? user.getName() : "Library Member")
                .userEmail(Objects.nonNull(user) ? user.getEmail() : "")
                .amount(fineAmount)
                .pendingFineAmount(fineAmount)
                .status(fine.getStatus())
                .createdAt(fine.getCreatedAt())
                .updatedAt(fine.getUpdatedAt())
                .build();
    }

    /**
     * Retrieves all fines formatted as response DTOs.
     *
     * @return List of {@link FineResponseDto}s
     */
    public List<FineResponseDto> getAllFinesDto() {
        return fineRepository.findAllByOrderByIdDesc().stream()
                .map(this::mapToDtoWithDetails)
                .toList();
    }

    /**
     * Retrieves all fines formatted as DTOs for a specific user ID.
     *
     * @param userId The database user ID
     * @return List of {@link FineResponseDto}s
     */
    public List<FineResponseDto> getFinesDtoByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuidOrderByIdDesc(user.getUuid()).stream()
                .map(this::mapToDtoWithDetails)
                .toList();
    }

    /**
     * Retrieves all fines formatted as DTOs for a specific user email.
     *
     * @param email The user email
     * @return List of {@link FineResponseDto}s
     */
    public List<FineResponseDto> getFinesDtoByUserEmail(String email) {
        if (Objects.isNull(email)) {
            return java.util.Collections.emptyList();
        }
        User user = userProviderRepository.findByProviderId(email)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);
        return (Objects.isNull(user) || Objects.isNull(user.getId()))
                ? Collections.emptyList()
                : getFinesDtoByUserId(user.getId());
    }

    /**
     * Retrieves fine entities for a specific user ID.
     *
     * @param userId The user ID
     * @return List of {@link Fine} entities
     */
    public List<Fine> getFinesByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuid(user.getUuid());
    }

    /**
     * Retrieves pending fine entities for a specific user ID.
     *
     * @param userId The user ID
     * @return List of pending {@link Fine} entities
     */
    public List<Fine> getPendingFinesByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuidAndStatus(user.getUuid(), FineStatus.PENDING);
    }

    /**
     * Marks a fine record as PAID.
     *
     * @param fineId The fine ID
     * @return The updated {@link Fine} entity
     */
    @Transactional
    public Fine payFine(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new IllegalArgumentException("Fine record not found with id: " + fineId));
        fine.setStatus(FineStatus.PAID);
        log.info("Fine record {} marked as PAID with amount {} for bookUuid {} and userUuid {}",
                fineId, fine.getPendingFineAmount(), fine.getBookUuid(), fine.getUserUuid());
        return fineRepository.save(fine);
    }

    /**
     * Checks if a user has any outstanding pending fine amounts.
     *
     * @param userId The user ID
     * @return {@code true} if total pending fine is greater than zero
     */
    @Override
    public boolean hasPendingFines(Long userId) {
        BigDecimal totalPending = calculateTotalPendingFineForUser(userId);
        return Objects.nonNull(totalPending) && totalPending.compareTo(BigDecimal.ZERO) > 0;
    }
}
