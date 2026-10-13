package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.BookDonation;
import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookDonationRepository extends JpaRepository<BookDonation, String> {
    Optional<BookDonation> findByUuid(String uuid);
    Optional<BookDonation> findById(Long id);
    List<BookDonation> findAllByOrderByCreatedAtDesc();
    List<BookDonation> findByUser_UuidOrderByCreatedAtDesc(String userUuid);
    List<BookDonation> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<BookDonation> findByStatusOrderByCreatedAtDesc(DonationStatus status);
}
