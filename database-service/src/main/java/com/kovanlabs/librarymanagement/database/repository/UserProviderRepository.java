package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProviderRepository extends JpaRepository<UserProvider, UUID> {

    Optional<UserProvider> findByProviderId(String providerId);

    List<UserProvider> findByUserUuid(UUID userUuid);

    Optional<UserProvider> findByUserUuidAndProvider(UUID userUuid, AuthProvider provider);

    Optional<UserProvider> findByUuid(UUID uuid);

    Optional<UserProvider> findById(Long id);

    boolean existsByProviderId(String providerId);
}
