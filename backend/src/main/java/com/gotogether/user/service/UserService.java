package com.gotogether.user.service;

import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.common.pagination.OffsetCursor;
import com.gotogether.user.dto.AdminUserDetailResponse;
import com.gotogether.user.dto.SubmitVerificationRequest;
import com.gotogether.user.dto.UserResponse;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.dto.VerificationQueueEntry;
import com.gotogether.user.dto.VerificationResponse;
import com.gotogether.user.entity.RejectionReason;
import com.gotogether.user.entity.User;
import com.gotogether.user.entity.Verification;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.entity.VerificationStatus;
import com.gotogether.user.entity.VerificationType;
import com.gotogether.user.repository.UserRepository;
import com.gotogether.user.repository.VerificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user module's only entry point for other modules — everything else in
 * {@code com.gotogether.user} (entities, repositories) is package-private to
 * this module in practice (enforced by {@code ArchitectureTest}, not Java
 * visibility, since Spring Data repositories must be public interfaces).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;

    public UserService(UserRepository userRepository, VerificationRepository verificationRepository) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
    }

    /** Used by the auth module's Google Sign-In flow. Returns whether the user is newly created, for profile bootstrapping. */
    @Transactional
    public FindOrCreateResult findOrCreateByGoogleId(String googleId, String email) {
        return userRepository.findByGoogleId(googleId)
                .map(existing -> new FindOrCreateResult(toSummary(existing), false))
                .orElseGet(() -> {
                    User created = userRepository.save(User.newGoogleUser(googleId, email));
                    recordAutoVerification(created, VerificationType.EMAIL);
                    return new FindOrCreateResult(toSummary(created), true);
                });
    }

    /** Used by the auth module's Phone OTP flow. */
    @Transactional
    public FindOrCreateResult findOrCreateByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(existing -> new FindOrCreateResult(toSummary(existing), false))
                .orElseGet(() -> {
                    User created = userRepository.save(User.newPhoneUser(phoneNumber));
                    recordAutoVerification(created, VerificationType.PHONE);
                    return new FindOrCreateResult(toSummary(created), true);
                });
    }

    public UserSummary getSummary(UUID userId) {
        return toSummary(getUserOrThrow(userId));
    }

    /** Account age input for the {@code trust} module's "Account age & activity" component (10% weight) — see {@code TrustService}'s class doc for why "activity consistency" (the other half of that factor) isn't tracked yet. */
    public OffsetDateTime getAccountCreatedAt(UUID userId) {
        return getUserOrThrow(userId).getCreatedAt();
    }

    public UserResponse getMe(UUID userId) {
        User user = getUserOrThrow(userId);
        return new UserResponse(
                user.getId(), user.getPhoneNumber(), user.getEmail(),
                user.getStatus(), user.getVerificationLevel(), user.getRole(), user.getCreatedAt());
    }

    @Transactional
    public void recordLogin(UUID userId) {
        User user = getUserOrThrow(userId);
        user.recordLogin();
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = getUserOrThrow(userId);
        user.deactivate();
        userRepository.save(user);
    }

    @Transactional
    public void reactivate(UUID userId) {
        User user = getUserOrThrow(userId);
        user.reactivate();
        userRepository.save(user);
    }

    /**
     * Soft-delete only — Business Rules Module 1 Section 10's 30-day
     * anonymization job is a separate scheduled process (Phase 9), not
     * triggered synchronously here.
     */
    @Transactional
    public void softDelete(UUID userId) {
        User user = getUserOrThrow(userId);
        user.markDeleted();
        userRepository.save(user);
    }

    @Transactional
    public VerificationResponse submitVerification(UUID userId, SubmitVerificationRequest request) {
        User user = getUserOrThrow(userId);
        Verification verification = Verification.submit(
                user, request.type(), request.documentType(), null, request.documentImageUrl());
        verificationRepository.save(verification);
        return toResponse(verification);
    }

    public List<VerificationResponse> listVerifications(UUID userId) {
        return verificationRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Phone/email are auto-approved as a direct side effect of the auth flow
     * (OTP verified = phone confirmed; Google Sign-In = email confirmed by
     * Google) — never submitted through {@link #submitVerification}.
     * Government ID / selfie match both advance to {@link VerificationLevel#ID_APPROVED}
     * once a moderator approves them (Phase 8); this method only handles the
     * two auto-approved types.
     */
    @Transactional
    public void recordAutoVerification(User user, VerificationType type) {
        Verification verification = Verification.autoApprove(user, type);
        verificationRepository.save(verification);
        bumpVerificationLevel(user, levelFor(type));
        userRepository.save(user);
    }

    // --- Phase 8 admin entry points (called by admin.service.AdminService — see AdminUserDetailResponse's class doc) ---

    /** {@code GET /admin/users/{id}} (API Spec Section 16) — the account-identity half; {@code AdminService} layers the Trust Score half on top. */
    public AdminUserDetailResponse getAdminDetail(UUID userId) {
        User user = getUserOrThrow(userId);
        return new AdminUserDetailResponse(
                user.getId(), user.getPhoneNumber(), user.getEmail(), user.getStatus(), user.getVerificationLevel(),
                user.getRole(), user.getCreatedAt(), user.getLastLoginAt(), user.getDeactivatedAt(),
                user.getDeletedAt(), listVerifications(userId));
    }

    /** {@code POST /admin/users/{id}/restrict} (API Spec Section 16, Operations Module B's Warning &amp; Enforcement Ladder Restricted tier). */
    @Transactional
    public UserResponse adminRestrict(UUID userId) {
        User user = getUserOrThrow(userId);
        user.restrict();
        return getMe(userRepository.save(user).getId());
    }

    /** {@code POST /admin/users/{id}/suspend} (API Spec Section 16, Suspended tier). */
    @Transactional
    public UserResponse adminSuspend(UUID userId) {
        User user = getUserOrThrow(userId);
        user.suspend();
        return getMe(userRepository.save(user).getId());
    }

    /** Permanently removed tier — see {@code User#adminRemove}'s doc for why this reuses the soft-delete path. */
    @Transactional
    public UserResponse adminRemove(UUID userId) {
        User user = getUserOrThrow(userId);
        user.adminRemove();
        return getMe(userRepository.save(user).getId());
    }

    /** {@code GET /admin/dashboard}'s pending-user-verifications count (Phase 8). */
    public long countPendingVerifications() {
        return verificationRepository.countByStatus(VerificationStatus.PENDING);
    }

    /** {@code GET /admin/analytics?metric=signups} (Phase 9, Operations Module D's "Growth" category: "New signups"). */
    public long countSignupsBetween(OffsetDateTime from, OffsetDateTime to) {
        return userRepository.countByCreatedAtBetween(from, to);
    }

    /** {@code GET /admin/verifications} (API Spec Section 16) — the individual ID/business verification review queue. */
    @Transactional
    public CursorPageResponse<VerificationQueueEntry> getVerificationQueue(String cursor, int limit) {
        int effectiveLimit = limit <= 0 ? 20 : limit;
        int offset = OffsetCursor.decode(cursor);
        var page = verificationRepository.findByStatusOrderByCreatedAtAsc(
                VerificationStatus.PENDING, PageRequest.of(offset / Math.max(effectiveLimit, 1), effectiveLimit));
        var items = page.getContent().stream()
                .map(v -> new VerificationQueueEntry(
                        v.getId(), v.getUserId(), v.getType(), v.getDocumentType(), v.getDocumentImageUrl(), v.getCreatedAt()))
                .toList();
        String nextCursor = page.hasNext() ? OffsetCursor.encode(offset + effectiveLimit) : null;
        return CursorPageResponse.of(items, nextCursor);
    }

    /** {@code POST /admin/verifications/{id}/approve} (API Spec Section 16, Chapter 3 §3.6) — advances {@link VerificationLevel} exactly like the auto-approved phone/email path (see {@link #bumpVerificationLevel}). */
    @Transactional
    public VerificationResponse approveVerification(UUID verificationId, UUID reviewerId) {
        Verification verification = getVerificationOrThrow(verificationId);
        verification.approve(reviewerId);
        verificationRepository.save(verification);
        User user = verification.getUser();
        bumpVerificationLevel(user, levelFor(verification.getType()));
        userRepository.save(user);
        return toResponse(verification);
    }

    /**
     * {@code POST /admin/verifications/{id}/reject} (API Spec Section 16) —
     * {@code rejectionReasonRaw} is optional per the API Spec's {@code {
     * rejection_reason? }} request shape, and a {@code String} rather than
     * this module's own {@link RejectionReason} enum — {@code
     * admin.service.AdminService} lives in a different module, and taking
     * the enum directly there would be exactly the cross-module entity
     * access {@code ArchitectureTest} forbids (same reason {@code
     * CompanyController.myTrips} takes a {@code String status} instead of
     * {@code trip.entity.TripStatus}, and {@code
     * NotificationService#create} takes a {@code String type}).
     */
    @Transactional
    public VerificationResponse rejectVerification(UUID verificationId, UUID reviewerId, String rejectionReasonRaw) {
        Verification verification = getVerificationOrThrow(verificationId);
        RejectionReason rejectionReason = rejectionReasonRaw == null ? null : RejectionReason.valueOf(rejectionReasonRaw.toUpperCase());
        verification.reject(reviewerId, rejectionReason);
        verificationRepository.save(verification);
        return toResponse(verification);
    }

    private Verification getVerificationOrThrow(UUID verificationId) {
        return verificationRepository.findById(verificationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Verification", verificationId));
    }

    private void bumpVerificationLevel(User user, VerificationLevel candidate) {
        if (candidate.ordinal() > user.getVerificationLevel().ordinal()) {
            user.setVerificationLevel(candidate);
        }
    }

    private VerificationLevel levelFor(VerificationType type) {
        return switch (type) {
            case PHONE -> VerificationLevel.PHONE;
            case EMAIL -> VerificationLevel.EMAIL;
            case GOVERNMENT_ID, SELFIE_MATCH -> VerificationLevel.ID_APPROVED;
        };
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getRole(), user.getStatus(), user.getVerificationLevel());
    }

    private VerificationResponse toResponse(Verification v) {
        return new VerificationResponse(
                v.getId(), v.getType(), v.getStatus(), v.getRejectionReason(), v.getCreatedAt(), v.getReviewedAt());
    }

    /** Whether a brand-new {@code User} row was just created, so the auth module knows to bootstrap a profile. */
    public record FindOrCreateResult(UserSummary summary, boolean newlyCreated) {}
}
