package com.kovanlabs.librarymanagement.borrow.service;

import com.kovanlabs.librarymanagement.borrow.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.borrow.event.BookReturnedEvent;
import com.kovanlabs.librarymanagement.borrow.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import com.kovanlabs.librarymanagement.notification.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.kovanlabs.librarymanagement.book.entity.Book;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final NotificationFactory notificationFactory;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserFineChecker userFineChecker;

    @Override
    @Transactional
    public BorrowResponseDto borrowBook(BorrowRequestDto request) {

        Users users = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + request.userId()));

        if (userFineChecker != null && userFineChecker.hasPendingFines(request.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has pending fines. Please pay outstanding fines before borrowing another book.");
        }

        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + request.bookId()));
        Borrow borrow = Borrow.builder()
                .users(users)
                .book(book)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();
        borrowRepository.save(borrow);

        NotificationRequest notificationRequest = new NotificationRequest(
                users.getEmail(),
                "Book Borrowed",
                "You have successfully borrowed \"" +book.getTitle() + "\". Return it before " + borrow.getDueDate()
        );

        notificationFactory.get(NotificationTypeEnum.EMAIL).send(notificationRequest);

        return mapToResponse(borrow);
    }

    @Transactional
    public BorrowResponseDto returnBook(Long borrowId) {

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow record not found with ID: " + borrowId));

        if (userFineChecker != null && borrow.getUsers() != null && userFineChecker.hasPendingFines(borrow.getUsers().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot return book while having pending fines. Please pay outstanding fines first.");
        }

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);
        borrowRepository.save(borrow);

        applicationEventPublisher.publishEvent(new BookReturnedEvent(borrowId));

        return mapToResponse(borrow);
    }


    private BorrowResponseDto mapToResponse(Borrow borrow) {

        return BorrowResponseDto.builder()
                .borrowId(borrow.getId())
                .userId(borrow.getUsers().getId())
                .bookId(borrow.getBook().getId())
                .borrowDate(borrow.getBorrowDate())
                .dueDate(borrow.getDueDate())
                .returnedDate(borrow.getReturnedDate())
                .status(borrow.getStatus())
                .build();
    }
}