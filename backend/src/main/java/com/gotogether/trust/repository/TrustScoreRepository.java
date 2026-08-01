package com.gotogether.trust.repository;

import com.gotogether.trust.entity.TrustLevel;
import com.gotogether.trust.entity.TrustScore;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface TrustScoreRepository extends JpaRepository<TrustScore, UUID> {

    /** {@code GET /admin/analytics?metric=trust_score_distribution} (Phase 9, Operations Module D's "Trust" category: "Trust Score distribution across the platform"). */
    long countByLevel(TrustLevel level);
}
