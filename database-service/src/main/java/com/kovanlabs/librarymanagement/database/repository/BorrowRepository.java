package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowRepository extends JpaRepository<Borrow, String> {
    Optional<Borrow> findByUuid(String uuid);
    Optional<Borrow> findById(Long id);
    List<Borrow> findByReturnedDateIsNullAndDueDateBefore(LocalDate date);
    List<Borrow> findAllByOrderByIdDesc();
    List<Borrow> findByUser_IdOrderByIdDesc(Long userId);
    List<Borrow> findByUser_UuidOrderByIdDesc(String userUuid);
    long countByUser_UuidAndStatus(String userUuid, BorrowStatus status);
    List<Borrow> findByBook_UuidAndUser_Uuid(String bookUuid, String userUuid);
    Optional<Borrow> findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(String bookUuid, String userUuid);
    List<Borrow> findBySalesforceSyncStatus(SalesforceSyncStatus salesforceSyncStatus);

    @Query("""
        SELECT b FROM Borrow b 
        WHERE b.status = com.kovanlabs.librarymanagement.database.enums.BorrowStatus.RETURNED 
          AND b.returnedDate IS NOT NULL 
          AND b.returnedDate <= b.dueDate 
          AND b.rewardProcessed = false
    """)
    List<Borrow> findUnprocessedOnTimeBorrows();

    @Modifying
    @Query("UPDATE Borrow b SET b.rewardProcessed = true WHERE b.uuid IN :uuids")
    int markBorrowsAsRewardProcessed(@Param("uuids") List<String> uuids);
}
