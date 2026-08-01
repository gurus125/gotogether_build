package com.gotogether.trust.repository;

import com.gotogether.trust.entity.TrustScoreHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface TrustScoreHistoryRepository extends JpaRepository<TrustScoreHistory, UUID> {

    /** {@code GET /users/me/trust-score/history} (API Spec Section 12) — self-only trajectory, newest-first. */
    Page<TrustScoreHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
