package com.kovanlabs.librarymanagement.user.repository;

import com.kovanlabs.librarymanagement.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
