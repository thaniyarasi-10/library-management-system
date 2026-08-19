package com.kovanlabs.librarymanagement.borrow.repository;

import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BorrowRepository extends JpaRepository<Borrow, Long> {
    List<Borrow> findByReturnedDateIsNullAndDueDateBefore(LocalDate date);
}
