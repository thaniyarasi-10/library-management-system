package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.mapping.BorrowMapper;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSyncService;
import com.kovanlabs.librarymanagement.service.MembershipService;
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
    private final UserFineChecker userFineChecker;
    private final MembershipService membershipService;
    private final SalesforceSyncService salesforceSyncService;

    /**
     * Validates membership status and pending fines, persists borrow record, and syncs with Salesforce.
     *
     * @param borrowRequestDto The borrow payload
     * @return Created {@link BorrowResponseDto}
     */
    @Override
    public BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto) {
        if (borrowRequestDto == null || borrowRequestDto.userId() == null || borrowRequestDto.bookId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId and userId are required");
        }

        User user = userRepository.findById(borrowRequestDto.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + borrowRequestDto.userId()));

        if (!membershipService.hasActiveMembership(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only users with an ACTIVE membership can perform borrow operations");
        }

        if (userFineChecker != null && userFineChecker.hasPendingFines(borrowRequestDto.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before borrowing books.");
        }

        Book book = bookRepository.findById(borrowRequestDto.bookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Book not found with id: " + borrowRequestDto.bookId()));

        Borrow borrow = BorrowMapper.INSTANCE.mapToEntity(borrowRequestDto, book, user);

        Borrow savedBorrow = borrowRepository.save(borrow);
        BorrowResponseDto response = BorrowMapper.INSTANCE.mapToResponse(savedBorrow);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(response));
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for borrow creation: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Returns a borrowed book, updates return date and status, and syncs with Salesforce.
     *
     * @param borrowId The borrow record ID
     * @return Updated {@link BorrowResponseDto}
     */
    @Override
    public BorrowResponseDto returnBook(Long borrowId) {
        if (borrowId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowId is required");
        }

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow record not found"));

        if (userFineChecker != null && borrow.getUser() != null
                && userFineChecker.hasPendingFines(borrow.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User has pending fines. Please pay outstanding fines before returning books.");
        }

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);

        Borrow updatedBorrow = borrowRepository.save(borrow);
        BorrowResponseDto response = BorrowMapper.INSTANCE.mapToResponse(updatedBorrow);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBorrow(BorrowMapper.INSTANCE.toBorrowSObject(response));
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for borrow return: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Retrieves all borrow records from Salesforce SOQL if available, otherwise from MySQL.
     *
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public java.util.List<BorrowResponseDto> getAllBorrows() {
        if (salesforceSyncService != null) {
            try {
                var sfBorrowModels = salesforceSyncService.fetchBorrowsFromSalesforce();
                if (sfBorrowModels != null && !sfBorrowModels.isEmpty()) {
                    List<BorrowResponseDto> sfBorrows = BorrowMapper.INSTANCE.toBorrowResponseList(sfBorrowModels);
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} borrow records from Salesforce SOQL", sfBorrows.size());
                    return sfBorrows;
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for borrows, falling back to MySQL: {}", e.getMessage());
            }
        }
        log.info("[DATA SOURCE: MYSQL] Fetching borrow records from MySQL database");
        return borrowRepository.findAllByOrderByIdDesc().stream()
                .map(BorrowMapper.INSTANCE::mapToResponse)
                .toList();
    }

    /**
     * Retrieves all borrow records for a specific user ID.
     *
     * @param userId The user database ID
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public java.util.List<BorrowResponseDto> getBorrowsByUserId(Long userId) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        return borrowRepository.findByUser_IdOrderByIdDesc(userId).stream()
                .map(BorrowMapper.INSTANCE::mapToResponse)
                .toList();
    }

    /**
     * Retrieves all borrow records for a specific user email.
     *
     * @param email The user email
     * @return List of {@link BorrowResponseDto}s
     */
    @Override
    public java.util.List<BorrowResponseDto> getBorrowsByUserEmail(String email) {
        if (email == null) {
            return java.util.Collections.emptyList();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getId() == null) {
            return java.util.Collections.emptyList();
        }
        return getBorrowsByUserId(user.getId());
    }
}
