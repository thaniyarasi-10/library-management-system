package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserFineChecker userFineChecker;

    @Override
    public BorrowResponseDto borrowBook(BorrowRequestDto borrowRequestDto) {
        if (borrowRequestDto == null || borrowRequestDto.userId() == null || borrowRequestDto.bookId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId and userId are required");
        }

        User user = userRepository.findById(borrowRequestDto.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + borrowRequestDto.userId()));

        if (userFineChecker != null && userFineChecker.hasPendingFines(borrowRequestDto.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has pending fines. Please pay outstanding fines before borrowing books.");
        }

        Book book = bookRepository.findById(borrowRequestDto.bookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with id: " + borrowRequestDto.bookId()));

        Borrow borrow = Borrow.builder()
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();

        Borrow savedBorrow = borrowRepository.save(borrow);

        return BorrowResponseDto.builder()
                .borrowUuid(savedBorrow.getUuid())
                .id(savedBorrow.getId())
                .bookId(savedBorrow.getBook().getUuid())
                .userId(savedBorrow.getUser().getUuid())
                .borrowDate(savedBorrow.getBorrowDate())
                .dueDate(savedBorrow.getDueDate())
                .status(savedBorrow.getStatus())
                .build();
    }

    @Override
    public BorrowResponseDto returnBook(Long borrowId) {
        if (borrowId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowId is required");
        }

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow record not found"));

        if (userFineChecker != null && borrow.getUser() != null && userFineChecker.hasPendingFines(borrow.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has pending fines. Please pay outstanding fines before returning books.");
        }

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);

        Borrow updatedBorrow = borrowRepository.save(borrow);

        return BorrowResponseDto.builder()
                .borrowUuid(updatedBorrow.getUuid())
                .id(updatedBorrow.getId())
                .bookId(updatedBorrow.getBook().getUuid())
                .userId(updatedBorrow.getUser().getUuid())
                .borrowDate(updatedBorrow.getBorrowDate())
                .dueDate(updatedBorrow.getDueDate())
                .returnedDate(updatedBorrow.getReturnedDate())
                .status(updatedBorrow.getStatus())
                .build();
    }
}
