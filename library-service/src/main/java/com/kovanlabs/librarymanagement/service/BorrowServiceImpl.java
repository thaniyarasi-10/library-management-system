package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * Validates membership status and pending fines, persists borrow record, and syncs with Salesforce.
     *
     * @param borrowRequestDto The borrow payload
     * @return Created {@link BorrowResponseDto}
     */
    @Override
    public BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto) {
        if (Objects.isNull(borrowRequestDto) || Objects.isNull(borrowRequestDto.userId()) || Objects.isNull(borrowRequestDto.bookId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId and userId are required");
        }

        User user = userRepository.findById(borrowRequestDto.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + borrowRequestDto.userId()));

        if (!membershipService.hasActiveMembership(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only users with an ACTIVE membership can perform borrow operations");
        }

        if (Objects.nonNull(userFineChecker) && userFineChecker.hasPendingFines(borrowRequestDto.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before borrowing books.");
        }

        Book book = bookRepository.findById(borrowRequestDto.bookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Book not found with id: " + borrowRequestDto.bookId()));

        Borrow borrow = BorrowMapper.INSTANCE.mapToEntity(borrowRequestDto, book, user);

        Borrow savedBorrow = borrowRepository.save(borrow);

        // Salesforce Sync
        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(BorrowMapper.INSTANCE.mapToResponse(savedBorrow)));
                savedBorrow.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                borrowRepository.save(savedBorrow);
            } catch (Exception e) {
                int retryCount = savedBorrow.getSalesforceRetryCount() + 1;
                savedBorrow.setSalesforceRetryCount(retryCount);
                savedBorrow.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                borrowRepository.save(savedBorrow);
                log.error(
                        "Salesforce dual-write failed for borrow creation [Borrow ID: {}, UUID: {}, Operation: CREATE, RetryCount: {}]: {}",
                        savedBorrow.getId(), savedBorrow.getUuid(), retryCount, e.getMessage());
            }
        }

        return BorrowMapper.INSTANCE.mapToResponse(savedBorrow);
    }

    /**
     * Returns a borrowed book, updates return timestamp, and syncs status with Salesforce.
     *
     * @param borrowId ID of the borrow record
     * @return Updated {@link BorrowResponseDto}
     */
    @Override
    public BorrowResponseDto returnBook(Long borrowId) {
        if (Objects.isNull(borrowId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowId is required");
        }

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow record not found"));

        if (Objects.nonNull(userFineChecker) && Objects.nonNull(borrow.getUser()) && userFineChecker.hasPendingFines(borrow.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before returning books.");
        }

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);

        Borrow updatedBorrow = borrowRepository.save(borrow);

        // Salesforce Sync
        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(BorrowMapper.INSTANCE.mapToResponse(updatedBorrow)));
                updatedBorrow.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                borrowRepository.save(updatedBorrow);
            } catch (Exception e) {
                int retryCount = updatedBorrow.getSalesforceRetryCount() + 1;
                updatedBorrow.setSalesforceRetryCount(retryCount);
                updatedBorrow.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                borrowRepository.save(updatedBorrow);
                log.error(
                        "Salesforce dual-write failed for borrow return [Borrow ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
                        updatedBorrow.getId(), updatedBorrow.getUuid(), retryCount, e.getMessage());
            }
        }

        return BorrowMapper.INSTANCE.mapToResponse(updatedBorrow);
    }

    /**
     * Retrieves all borrow records across the system in descending order of ID.
     *
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public List<BorrowResponseDto> getAllBorrows() {
        return borrowRepository.findAllByOrderByIdDesc().stream()
                .map(BorrowMapper.INSTANCE::mapToResponse)
                .toList();
    }

    /**
     * Retrieves all borrow records for a specific user ID.
     *
     * @param userId The user's ID
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public List<BorrowResponseDto> getBorrowsByUserId(Long userId) {
        return Objects.isNull(userId)
                ? Collections.emptyList()
                : borrowRepository.findByUser_IdOrderByIdDesc(userId).stream()
                        .map(BorrowMapper.INSTANCE::mapToResponse)
                        .toList();
    }

    /**
     * Retrieves all borrow records for a specific user identified by email or providerId.
     *
     * @param email The user's email or provider ID
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public java.util.List<BorrowResponseDto> getBorrowsByUserEmail(String email) {
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
}
