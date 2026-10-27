package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.UserPaymentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPaymentProfileRepository extends JpaRepository<UserPaymentProfile, String> {

    Optional<UserPaymentProfile> findByUserUuid(String userUuid);

    boolean existsByUserUuid(String userUuid);

    void deleteByUserUuid(String userUuid);
}
