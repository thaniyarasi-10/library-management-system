package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.book.service.BookService;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.entity.User;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BookService bookService;

    @Override
    @Transactional
    public UserResponse createUser(UserRequest request) {
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .build();
        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        
        user.setName(request.name());
        user.setEmail(request.email());
        
        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public UserResponse borrowBook(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));
        BookResponse bookResponse = bookService.getBookById(bookId);
        Book book = Book.builder()
                .id(bookResponse.id())
                .title(bookResponse.title())
                .author(bookResponse.author())
                .isbn(bookResponse.isbn())
                .build();
        
        if (user.getBorrowedBooks().stream().noneMatch(b -> b.getId().equals(bookId))) {
            user.getBorrowedBooks().add(book);
            userRepository.save(user);
        }
        
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse returnBook(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));
        
        bookService.getBookById(bookId);
        user.getBorrowedBooks().removeIf(b -> b.getId().equals(bookId));
        userRepository.save(user);
        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        List<BookResponse> borrowed = user.getBorrowedBooks() != null ? user.getBorrowedBooks().stream()
                .map(book -> new BookResponse(
                        book.getId(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getIsbn()
                ))
                .collect(Collectors.toList()) : new ArrayList<>();

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                borrowed
        );
    }
}
