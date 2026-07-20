package com.kovanlabs.librarymanagement.book.repository;

import com.kovanlabs.librarymanagement.book.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}
