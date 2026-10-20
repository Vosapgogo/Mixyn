package com.mixyn.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Whether someone else already has this username. */
    boolean existsByUsernameAndIdNot(String username, Long id);
}
