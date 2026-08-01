package com.gotogether.user.repository;

import com.gotogether.user.entity.Verification;
import com.gotogether.user.entity.VerificationStatus;
import com.gotogether.user.entity.VerificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    // `User_Id`, not `UserId` — Verification.user is a @ManyToOne relation
    // (the one entity in this codebase that models a user reference that way
    // instead of a raw UUID column), and Spring Data's property-path parser
    // could not resolve a bare `UserId` token against it at repository-proxy
    // creation time ("Could not resolve attribute 'userId' of
    // 'com.gotogether.user.entity.Verification'") — caught only once the
    // user ran a real `mvn spring-boot:run`, since Spring context startup
    // isn't exercised by this sandbox's manual-review-only verification.
    // The underscore forces Spring Data to traverse the `user` association
    // and match its `id` property explicitly, rather than guessing.
    List<Verification> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Verification> findFirstByUser_IdAndTypeOrderByCreatedAtDesc(UUID userId, VerificationType type);

    /** {@code GET /admin/verifications} (Phase 8) — the ID/business verification review queue's individual-user half; only {@code government_id}/{@code selfie_match} ever reach {@code PENDING} ({@link com.gotogether.user.service.UserService#recordAutoVerification}'s phone/email path never does), so no {@code type} filter is needed on top of status. */
    Page<Verification> findByStatusOrderByCreatedAtAsc(VerificationStatus status, Pageable pageable);

    long countByStatus(VerificationStatus status);
}
