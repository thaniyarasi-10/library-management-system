package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final S3Service s3Service;

    @Override
    @Transactional
    public BookResponse createBook(BookRequest request) {
        Book book = Book.builder()
                .title(request.title())
                .author(request.author())
                .isbn(request.isbn())
                .build();
        Book savedBook = bookRepository.save(book);
        return mapToResponse(savedBook);
    }

    @Override
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        return mapToResponse(book);
    }

    @Override
    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id));
        
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        
        Book updatedBook = bookRepository.save(book);
        return mapToResponse(updatedBook);
    }

    @Override
    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: " + id);
        }
        bookRepository.deleteById(id);
    }

    private BookResponse mapToResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn()
        );
    }

    @Transactional
    public String uploadBookCover(Long bookId, MultipartFile file)  {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        String key = null;
        try {
            key = s3Service.uploadFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        book.setCoverImageKey(key);
        bookRepository.save(book);
        return key;
    }
}
