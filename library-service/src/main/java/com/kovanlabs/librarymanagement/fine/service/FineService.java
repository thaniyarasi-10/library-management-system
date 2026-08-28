package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.book.service.UserFineChecker;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
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
        Optional<Fine> optionalFine = fineRepository.findByBookUuidAndUserUuid(bookUuid, userUuid);
        Fine fine;
        if (optionalFine.isPresent()) {
            fine = optionalFine.get();
            fine.setPendingFineAmount(pendingAmount);
            fine.setStatus(FineStatus.PENDING);
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
        fine.setPendingFineAmount(BigDecimal.ZERO);
        fine.setStatus(FineStatus.PAID);
        log.info("Fine record {} marked as PAID for bookUuid {} and userUuid {}", fineId, fine.getBookUuid(), fine.getUserUuid());
        return fineRepository.save(fine);
    }

    @Override
    public boolean hasPendingFines(Long userId) {
        BigDecimal totalPending = calculateTotalPendingFineForUser(userId);
        return totalPending != null && totalPending.compareTo(BigDecimal.ZERO) > 0;
    }
}
