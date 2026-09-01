package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUuid(UUID uuid);
    Optional<Membership> findByMembershipId(Long membershipId);
    Optional<Membership> findByUserUuid(UUID userUuid);
    boolean existsByUserUuidAndStatusIn(UUID userUuid, Collection<MembershipStatus> statuses);
}
