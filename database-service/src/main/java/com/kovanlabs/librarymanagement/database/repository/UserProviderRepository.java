package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProviderRepository extends JpaRepository<UserProvider, String> {

    Optional<UserProvider> findByProviderId(String providerId);

    List<UserProvider> findByUserUuid(String userUuid);

    Optional<UserProvider> findByUserUuidAndProvider(String userUuid, AuthProvider provider);

    Optional<UserProvider> findByUuid(String uuid);

    Optional<UserProvider> findById(Long id);

    boolean existsByProviderId(String providerId);
}
