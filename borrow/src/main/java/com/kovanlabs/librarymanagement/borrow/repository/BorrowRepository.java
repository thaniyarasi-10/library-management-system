package com.kovanlabs.librarymanagement.borrow.repository;

import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRepository extends JpaRepository<Borrow,Long> {
    @Query("SELECT b FROM Borrow b WHERE " +
            "LOWER(b.users.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(b.users.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(b.book.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(b.book.author) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(b.book.isbn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "CAST(b.borrowDate AS string) LIKE CONCAT('%', :query, '%') OR " +
            "CAST(b.dueDate AS string) LIKE CONCAT('%', :query, '%') OR " +
            "CAST(b.returnedDate AS string) LIKE CONCAT('%', :query, '%') OR " +
            "LOWER(CAST(b.status AS string)) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Borrow> searchBorrowedBooks(@Param("query") String query, Pageable pageable);
}
