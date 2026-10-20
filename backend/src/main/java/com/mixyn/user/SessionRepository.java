package com.mixyn.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {

    /** Every session of the user (deleting the account). */
    void deleteByUserId(Long userId);

    /** Every session of the user but this one (a new password: other phones have to log in again). */
    void deleteByUserIdAndTokenNot(Long userId, String token);
}
