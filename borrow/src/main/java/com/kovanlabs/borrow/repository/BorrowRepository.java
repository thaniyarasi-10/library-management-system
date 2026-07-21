package com.kovanlabs.borrow.repository;

import com.kovanlabs.borrow.entity.Borrow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowRepository extends JpaRepository<Borrow,Long> {
}
