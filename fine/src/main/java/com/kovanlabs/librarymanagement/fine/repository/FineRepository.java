package com.kovanlabs.librarymanagement.fine.repository;

import com.kovanlabs.librarymanagement.fine.entity.Fine;
import com.kovanlabs.librarymanagement.fine.enums.FineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FineRepository extends JpaRepository<Fine, Long> {

    Optional<Fine> findByBookIdAndUserId(Long bookId, Long userId);

    List<Fine> findByUserIdAndStatus(Long userId, FineStatus status);

    List<Fine> findByUserId(Long userId);
}