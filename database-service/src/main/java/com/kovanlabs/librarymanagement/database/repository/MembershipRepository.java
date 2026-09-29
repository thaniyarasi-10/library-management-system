package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, String> {
    Optional<Membership> findByUuid(String uuid);
    Optional<Membership> findById(Long id);
    Optional<Membership> findByMembershipId(Long membershipId);
    List<Membership> findByUserUuid(String userUuid);
    Optional<Membership> findTopByUserUuidOrderByCreatedAtDesc(String userUuid);
    Optional<Membership> findTopByUserUuidAndStatusInOrderByCreatedAtDesc(String userUuid, Collection<MembershipStatus> statuses);
    boolean existsByUserUuidAndStatusIn(String userUuid, Collection<MembershipStatus> statuses);
}
