package com.kovanlabs.librarymanagement.borrow.repository;

import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    @Query("SELECT b FROM Borrow b WHERE " +
           "LOWER(b.book.title) LIKE LOWER(:query) ESCAPE '\\' OR " +
           "LOWER(b.book.author) LIKE LOWER(:query) ESCAPE '\\' OR " +
           "LOWER(b.book.isbn) LIKE LOWER(:query) ESCAPE '\\' OR " +
           "LOWER(b.users.name) LIKE LOWER(:query) ESCAPE '\\' OR " +
           "LOWER(b.users.email) LIKE LOWER(:query) ESCAPE '\\'")
    Page<Borrow> searchBorrowedBooks(@Param("query") String query, Pageable pageable);
}
