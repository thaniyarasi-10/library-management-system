package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FineRepository extends JpaRepository<Fine, String> {

    Optional<Fine> findByUuid(String uuid);

    Optional<Fine> findById(Long id);

    Optional<Fine> findTopByBookUuidAndUserUuidOrderByIdDesc(String bookUuid, String userUuid);
    Optional<Fine> findByBookUuidAndUserUuid(String bookUuid, String userUuid);

    List<Fine> findByUserUuidAndStatus(String userUuid, FineStatus status);

    List<Fine> findByUserUuid(String userUuid);
    List<Fine> findAllByOrderByIdDesc();
    List<Fine> findByUserUuidOrderByIdDesc(String userUuid);
}
