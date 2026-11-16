package com.shoppulse.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence access for users; email is the natural login key. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
