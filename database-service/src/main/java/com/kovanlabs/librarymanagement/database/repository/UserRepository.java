package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUuid(String uuid);
    Optional<User> findById(Long id);
    boolean existsByEmail(String email);
    List<User> findBySalesforceSyncStatus(SalesforceSyncStatus salesforceSyncStatus);
    List<User> findByStatus(AccountStatus status);
    Page<User> findByStatus(AccountStatus status, Pageable pageable);
    List<User> findByStatusAndReactivationRequestedTrue(AccountStatus status);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.status = :status
          AND ((u.lastLoginAt IS NOT NULL AND u.lastLoginAt <= :cutoff)
               OR (u.lastLoginAt IS NULL AND u.createdAt IS NOT NULL AND u.createdAt <= :cutoff))
    """)
    List<User> findUsersEligibleForDeactivation(
            @Param("status") AccountStatus status,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
        SELECT u FROM User u
        WHERE u.status = :status
          AND u.inactivityWarningSentAt IS NULL
          AND ((u.lastLoginAt IS NOT NULL AND u.lastLoginAt <= :warningCutoff AND u.lastLoginAt > :deactivationCutoff)
               OR (u.lastLoginAt IS NULL AND u.createdAt IS NOT NULL AND u.createdAt <= :warningCutoff AND u.createdAt > :deactivationCutoff))
    """)
    List<User> findUsersEligibleForInactivityWarning(
            @Param("status") AccountStatus status,
            @Param("warningCutoff") LocalDateTime warningCutoff,
            @Param("deactivationCutoff") LocalDateTime deactivationCutoff
    );

    @Query("""
        SELECT u FROM User u
        WHERE u.status = :status
          AND u.inactivityWarningSentAt IS NOT NULL
          AND ((u.lastLoginAt IS NOT NULL AND u.lastLoginAt > :warningCutoff)
               OR (u.lastLoginAt IS NULL AND u.createdAt IS NOT NULL AND u.createdAt > :warningCutoff))
    """)
    List<User> findActiveUsersWithPendingWarningToReset(
            @Param("status") AccountStatus status,
            @Param("warningCutoff") LocalDateTime warningCutoff
    );
}
