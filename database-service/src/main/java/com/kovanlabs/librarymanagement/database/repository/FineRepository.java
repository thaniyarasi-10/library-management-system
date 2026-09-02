package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FineRepository extends JpaRepository<Fine, UUID> {

    Optional<Fine> findByUuid(UUID uuid);

    Optional<Fine> findById(Long id);

    Optional<Fine> findByBookUuidAndUserUuid(UUID bookUuid, UUID userUuid);

    List<Fine> findByUserUuidAndStatus(UUID userUuid, FineStatus status);

    List<Fine> findByUserUuid(UUID userUuid);
    List<Fine> findAllByOrderByIdDesc();
    List<Fine> findByUserUuidOrderByIdDesc(UUID userUuid);
}
