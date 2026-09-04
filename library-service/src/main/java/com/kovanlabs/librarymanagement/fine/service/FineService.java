package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.book.service.UserFineChecker;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.fine.dto.FineResponseDto;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineService implements UserFineChecker {

    public static final double FINE_PER_DAY = 5.0;

    private final FineRepository fineRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRepository borrowRepository;

    public FineResult calculateFine(Borrow borrow) {
        if (borrow == null || borrow.getDueDate() == null) {
            return new FineResult(borrow, 0, 0.0);
        }

        LocalDate endDate = borrow.getReturnedDate() != null ? borrow.getReturnedDate() : LocalDate.now();
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(borrow.getDueDate(), endDate));
        double fine = daysOverdue * FINE_PER_DAY;
        return new FineResult(borrow, daysOverdue, fine);
    }

    @Transactional
    public Fine createOrUpdateFine(UUID bookUuid, UUID userUuid, BigDecimal pendingAmount) {
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

    @Transactional
    public Fine processFineForBorrow(Borrow borrow) {
        if (borrow == null) {
            log.warn("Cannot process fine for null borrow record");
            return null;
        }
        FineResult result = calculateFine(borrow);
        if (borrow.getBook() != null && borrow.getUser() != null) {
            UUID bookUuid = borrow.getBook().getUuid();
            UUID userUuid = borrow.getUser().getUuid();
            BigDecimal amount = BigDecimal.valueOf(result.fine());
            log.info("Processing fine for borrowUuid: {}, bookUuid: {}, userUuid: {}, overdueDays: {}, calculated fine: {}",
                    borrow.getUuid(), bookUuid, userUuid, result.daysOverdue(), result.fine());
            return createOrUpdateFine(bookUuid, userUuid, amount);
        } else {
            log.warn("Cannot process fine for borrowUuid: {} because book or user reference is null", borrow.getUuid());
        }
        return null;
    }

    public BigDecimal calculateTotalPendingFineForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return calculateTotalPendingFineForUser(user.getUuid());
    }

    public BigDecimal calculateTotalPendingFineForUser(UUID userUuid) {
        List<Fine> pendingFines = fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING);
        return pendingFines.stream()
                .map(Fine::getPendingFineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public FineResponseDto mapToDtoWithDetails(Fine fine) {
        if (fine == null) return null;
        Book book = fine.getBookUuid() != null ? bookRepository.findByUuid(fine.getBookUuid()).orElse(null) : null;
        User user = fine.getUserUuid() != null ? userRepository.findByUuid(fine.getUserUuid()).orElse(null) : null;
        
        BigDecimal fineAmount = fine.getPendingFineAmount();
        // If the fine amount was previously zeroed out in the database by payFine, recover it from the borrow record
        if (fineAmount == null || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (fine.getBookUuid() != null && fine.getUserUuid() != null) {
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
            if (fineAmount == null || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                fineAmount = BigDecimal.valueOf(10.0);
            }
        }

        return FineResponseDto.builder()
                .uuid(fine.getUuid())
                .id(fine.getId())
                .bookUuid(fine.getBookUuid())
                .bookNumericId(book != null ? book.getId() : null)
                .bookTitle(book != null ? book.getTitle() : "Library Book")
                .bookAuthor(book != null ? book.getAuthor() : "Unknown Author")
                .bookCoverImageUrl(book != null ? book.getCoverImageUrl() : null)
                .userUuid(fine.getUserUuid())
                .userNumericId(user != null ? user.getId() : null)
                .userName(user != null ? user.getName() : "Library Member")
                .userEmail(user != null ? user.getEmail() : "")
                .amount(fineAmount)
                .pendingFineAmount(fineAmount)
                .status(fine.getStatus())
                .createdAt(fine.getCreatedAt())
                .updatedAt(fine.getUpdatedAt())
                .build();
    }

    public List<FineResponseDto> getAllFinesDto() {
        return fineRepository.findAllByOrderByIdDesc().stream()
                .map(this::mapToDtoWithDetails)
                .toList();
    }

    public List<FineResponseDto> getFinesDtoByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuidOrderByIdDesc(user.getUuid()).stream()
                .map(this::mapToDtoWithDetails)
                .toList();
    }

    public List<FineResponseDto> getFinesDtoByUserEmail(String email) {
        if (email == null) {
            return java.util.Collections.emptyList();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getId() == null) {
            return java.util.Collections.emptyList();
        }
        return getFinesDtoByUserId(user.getId());
    }

    public List<Fine> getFinesByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuid(user.getUuid());
    }

    public List<Fine> getPendingFinesByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        return fineRepository.findByUserUuidAndStatus(user.getUuid(), FineStatus.PENDING);
    }

    @Transactional
    public Fine payFine(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new IllegalArgumentException("Fine record not found with id: " + fineId));
        fine.setStatus(FineStatus.PAID);
        log.info("Fine record {} marked as PAID with amount {} for bookUuid {} and userUuid {}",
                fineId, fine.getPendingFineAmount(), fine.getBookUuid(), fine.getUserUuid());
        return fineRepository.save(fine);
    }

    @Override
    public boolean hasPendingFines(Long userId) {
        BigDecimal totalPending = calculateTotalPendingFineForUser(userId);
        return totalPending != null && totalPending.compareTo(BigDecimal.ZERO) > 0;
    }
}
