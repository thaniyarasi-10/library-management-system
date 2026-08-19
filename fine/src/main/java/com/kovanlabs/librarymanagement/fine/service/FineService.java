package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.borrow.service.UserFineChecker;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import com.kovanlabs.librarymanagement.fine.entity.Fine;
import com.kovanlabs.librarymanagement.fine.enums.FineStatus;
import com.kovanlabs.librarymanagement.fine.repository.FineRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FineService implements UserFineChecker {

    public static final double FINE_PER_DAY = 5.0;

    private final FineRepository fineRepository;

    public FineResult calculateFine(Borrow borrow) {
        if (borrow == null || borrow.getDueDate() == null) {
            return new FineResult(borrow, 0, 0.0);
        }

        LocalDate endDate = borrow.getReturnedDate() != null ? borrow.getReturnedDate() : LocalDate.now();
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(borrow.getDueDate(), endDate)); // to prevent it from going to negative
        double fine = daysOverdue * FINE_PER_DAY;
        return new FineResult(borrow, daysOverdue, fine);
    }

    @Transactional
    public Fine createOrUpdateFine(Long bookId, Long userId, BigDecimal pendingAmount) {
        Optional<Fine> optionalFine = fineRepository.findByBookIdAndUserId(bookId, userId);
        Fine fine;
        if (optionalFine.isPresent()) {
            fine = optionalFine.get();
            fine.setPendingFineAmount(pendingAmount);
            fine.setStatus(FineStatus.PENDING);
        } else {
            fine = Fine.builder()
                    .bookId(bookId)
                    .userId(userId)
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
        if (borrow.getBook() != null && borrow.getUsers() != null) {
            Long bookId = borrow.getBook().getId();
            Long userId = borrow.getUsers().getId();
            BigDecimal amount = BigDecimal.valueOf(result.fine());
            log.info("Processing fine for borrowId: {}, bookId: {}, userId: {}, overdueDays: {}, calculated fine: {}",
                    borrow.getId(), bookId, userId, result.daysOverdue(), result.fine());
            return createOrUpdateFine(bookId, userId, amount);
        } else {
            log.warn("Cannot process fine for borrowId: {} because book or user reference is null", borrow.getId());
        }
        return null;
    }

    public BigDecimal calculateTotalPendingFineForUser(Long userId) {
        List<Fine> pendingFines = fineRepository.findByUserIdAndStatus(userId, FineStatus.PENDING);
        return pendingFines.stream()
                .map(Fine::getPendingFineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Fine> getFinesByUserId(Long userId) {
        return fineRepository.findByUserId(userId);
    }

    public List<Fine> getPendingFinesByUserId(Long userId) {
        return fineRepository.findByUserIdAndStatus(userId, FineStatus.PENDING);
    }

    @Transactional
    public Fine payFine(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new IllegalArgumentException("Fine record not found with id: " + fineId));
        fine.setPendingFineAmount(BigDecimal.ZERO);
        fine.setStatus(FineStatus.PAID);
        log.info("Fine record {} marked as PAID for bookId {} and userId {}", fineId, fine.getBookId(), fine.getUserId());
        return fineRepository.save(fine);
    }

    @Transactional
    public Fine payFineByBookAndUser(Long bookId, Long userId) {
        Fine fine = fineRepository.findByBookIdAndUserId(bookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Fine record not found for bookId: " + bookId + " and userId: " + userId));
        fine.setPendingFineAmount(BigDecimal.ZERO);
        fine.setStatus(FineStatus.PAID);
        log.info("Fine record marked as PAID for bookId {} and userId {}", bookId, userId);
        return fineRepository.save(fine);
    }

    @Override
    public boolean hasPendingFines(Long userId) {
        BigDecimal totalPending = calculateTotalPendingFineForUser(userId);
        return totalPending != null && totalPending.compareTo(BigDecimal.ZERO) > 0;
    }
}