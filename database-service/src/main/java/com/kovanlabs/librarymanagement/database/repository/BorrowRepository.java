package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BorrowRepository extends JpaRepository<Borrow, UUID> {
    Optional<Borrow> findByUuid(UUID uuid);
    Optional<Borrow> findById(Long id);
    List<Borrow> findByReturnedDateIsNullAndDueDateBefore(LocalDate date);
    List<Borrow> findAllByOrderByIdDesc();
    List<Borrow> findByUser_IdOrderByIdDesc(Long userId);
    List<Borrow> findByUser_UuidOrderByIdDesc(UUID userUuid);
    List<Borrow> findByBook_UuidAndUser_Uuid(UUID bookUuid, UUID userUuid);
    Optional<Borrow> findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(UUID bookUuid, UUID userUuid);
}
