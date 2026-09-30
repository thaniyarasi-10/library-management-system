package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RewardRepository extends JpaRepository<Reward, String> {

    Optional<Reward> findByUuid(String uuid);

    Optional<Reward> findById(Long id);

    Optional<Reward> findByUserUuid(String userUuid);

    List<Reward> findByUserUuidIn(Collection<String> userUuids);

    @Modifying
    @Query("UPDATE Reward r SET r.points = r.points + :points, r.updatedAt = CURRENT_TIMESTAMP WHERE r.userUuid = :userUuid")
    int incrementPoints(@Param("userUuid") String userUuid, @Param("points") int points);
}
