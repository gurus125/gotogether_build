package com.gotogether.user.repository;

import com.gotogether.user.entity.User;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    /** {@code GET /admin/analytics?metric=signups} (Phase 9, Operations Module D's "Growth" category). */
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
