package com.kovanlabs.librarymanagement.borrow.repository;

import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowRepository extends JpaRepository<Borrow,Long> {
}
