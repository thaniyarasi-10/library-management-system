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

    @org.springframework.data.jpa.repository.Query("""
        SELECT b FROM Borrow b 
        WHERE b.status = com.kovanlabs.librarymanagement.database.enums.BorrowStatus.RETURNED 
          AND b.returnedDate IS NOT NULL 
          AND b.returnedDate <= b.dueDate 
          AND b.rewardProcessed = false
    """)
    List<Borrow> findUnprocessedOnTimeBorrows();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Borrow b SET b.rewardProcessed = true WHERE b.uuid IN :uuids")
    int markBorrowsAsRewardProcessed(@org.springframework.data.repository.query.Param("uuids") List<UUID> uuids);
}
