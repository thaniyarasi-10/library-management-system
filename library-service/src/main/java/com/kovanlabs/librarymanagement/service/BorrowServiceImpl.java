package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
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
import com.kovanlabs.librarymanagement.service.MembershipService;
import lombok.extern.slf4j.Slf4j;
import com.kovanlabs.librarymanagement.service.SalesforceSyncService;

@Service
@Slf4j
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserFineChecker userFineChecker;
    private final BookMapper bookMapper;
    private final MembershipService membershipService;
    private final SalesforceSyncService salesforceSyncService;

    public BorrowServiceImpl(
            BorrowRepository borrowRepository,
            BookRepository bookRepository,
            UserRepository userRepository,
            UserFineChecker userFineChecker,
            BookMapper bookMapper,
            MembershipService membershipService,
            SalesforceSyncService salesforceSyncService) {

        this.borrowRepository = borrowRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.userFineChecker = userFineChecker;
        this.bookMapper = bookMapper;
        this.membershipService = membershipService;
        this.salesforceSyncService = salesforceSyncService;
    }

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

        Borrow borrow = bookMapper.mapToEntity(borrowRequestDto, book, user);

        Borrow savedBorrow = borrowRepository.save(borrow);

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBorrow(savedBorrow);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for borrow creation: {}", e.getMessage());
            }
        }

        return bookMapper.mapToResponse(savedBorrow);
    }

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

        if (salesforceSyncService != null) {
            try {
                salesforceSyncService.syncBorrow(updatedBorrow);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for borrow return: {}", e.getMessage());
            }
        }

        return bookMapper.mapToResponse(updatedBorrow);
    }

    @Override
    public java.util.List<BorrowResponseDto> getAllBorrows() {
        if (salesforceSyncService != null) {
            try {
                java.util.List<BorrowResponseDto> sfBorrows = salesforceSyncService.fetchBorrowsFromSalesforce();
                if (sfBorrows != null && !sfBorrows.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} borrow records from Salesforce SOQL", sfBorrows.size());
                    return sfBorrows;
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for borrows, falling back to MySQL: {}", e.getMessage());
            }
        }
        log.info("[DATA SOURCE: MYSQL] Fetching borrow records from MySQL database");
        return borrowRepository.findAllByOrderByIdDesc().stream()
                .map(bookMapper::mapToResponse)
                .toList();
    }

    @Override
    public java.util.List<BorrowResponseDto> getBorrowsByUserId(Long userId) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        return borrowRepository.findByUser_IdOrderByIdDesc(userId).stream()
                .map(bookMapper::mapToResponse)
                .toList();
    }

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
