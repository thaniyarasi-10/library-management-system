package com.kovanlabs.librarymanagement.borrow.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.borrow.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "book", "users", "borrowDate", "dueDate", "returnedDate", "status"
    );

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final NotificationFactory notificationFactory;


    @Override
    @Transactional
    public BorrowResponseDto borrowBook(BorrowRequestDto request) {

        Users users = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + request.userId()));

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
                "You have successfully borrowed \"" + book.getTitle() + "\". Return it before " + borrow.getDueDate()
        );

        notificationFactory.get(NotificationTypeEnum.EMAIL).send(notificationRequest);

        return mapToResponse(borrow);
    }

    @Override
    @Transactional
    public BorrowResponseDto returnBook(Long borrowId) {

        Borrow borrow = borrowRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("Borrow record not found"));

        borrow.setReturnedDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);
        borrowRepository.save(borrow);
        return mapToResponse(borrow);
    }

    @Override
    public PagedResponse<BorrowResponseDto> searchBorrowedBooks(String query, int page, int size, String sortBy, String sortDir) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortBy field: " + sortBy + ". Allowed fields are: " + ALLOWED_SORT_FIELDS);
        }
        if (sortDir == null || (!sortDir.equalsIgnoreCase("ASC") && !sortDir.equalsIgnoreCase("DESC"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortDir: " + sortDir + ". Must be ASC or DESC");
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        String sanitizedQuery = escapeLikePattern(query);
        String searchPattern = "%" + sanitizedQuery + "%";
        Page<Borrow> borrowPage = borrowRepository.searchBorrowedBooks(searchPattern, pageable);
        List<BorrowResponseDto> content = borrowPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                borrowPage.getNumber(),
                borrowPage.getSize(),
                borrowPage.getTotalElements(),
                borrowPage.getTotalPages(),
                borrowPage.isLast()
        );
    }

    private String escapeLikePattern(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
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