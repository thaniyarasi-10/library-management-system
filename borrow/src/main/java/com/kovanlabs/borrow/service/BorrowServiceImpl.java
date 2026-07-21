package com.kovanlabs.borrow.service;

import com.kovanlabs.borrow.dto.BorrowRequestDto;
import com.kovanlabs.borrow.dto.BorrowResponseDto;
import com.kovanlabs.borrow.entity.Borrow;
import com.kovanlabs.borrow.enums.BorrowStatus;
import com.kovanlabs.borrow.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import com.kovanlabs.librarymanagement.user.entity.User;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.kovanlabs.librarymanagement.book.entity.Book;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BorrowResponseDto borrowBook(BorrowRequestDto request) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new RuntimeException("Book not found"));

        Borrow borrow = Borrow.builder()
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();
        borrowRepository.save(borrow);
        return mapToResponse(borrow);
    }

    @Transactional
    public BorrowResponseDto returnBook(Long borrowId) {

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("Borrow record not found"));

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);
        borrowRepository.save(borrow);
        return mapToResponse(borrow);
    }

    private BorrowResponseDto mapToResponse(Borrow borrow) {

        return BorrowResponseDto.builder()
                .borrowId(borrow.getId())
                .userId(borrow.getUser().getId())
                .bookId(borrow.getBook().getId())
                .borrowDate(borrow.getBorrowDate())
                .dueDate(borrow.getDueDate())
                .returnedDate(borrow.getReturnedDate())
                .status(borrow.getStatus())
                .build();
    }
}