package com.gotogether.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.company.dto.ApplyCompanyRequest;
import com.gotogether.company.dto.CompanyDocumentRef;
import com.gotogether.company.dto.InviteStaffRequest;
import com.gotogether.company.entity.CompanyUser;
import com.gotogether.company.entity.CompanyUserRole;
import com.gotogether.company.entity.CompanyVerification;
import com.gotogether.company.entity.TravelCompany;
import com.gotogether.company.repository.CompanyUserRepository;
import com.gotogether.company.repository.CompanyVerificationRepository;
import com.gotogether.company.repository.TravelCompanyRepository;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code enforceCompanyVerification} is set explicitly via {@link
 * ReflectionTestUtils} in every test that exercises {@link
 * CompanyService#assertVerified} — {@code @Value} is only processed inside a
 * real Spring context, so a plain {@code new CompanyService(...)} leaves that
 * field at Java's default ({@code false}), which would silently no-op the
 * check being tested otherwise. (This appears to be a real, pre-existing gap
 * in {@code TripServiceTest}'s equivalent {@code enforceIdApproval} tests —
 * flagged rather than silently repeated here.)
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock private TravelCompanyRepository travelCompanyRepository;
    @Mock private CompanyUserRepository companyUserRepository;
    @Mock private CompanyVerificationRepository companyVerificationRepository;
    @Mock private UserService userService;

    private CompanyService companyService;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(travelCompanyRepository, companyUserRepository, companyVerificationRepository, userService);
        lenient().when(travelCompanyRepository.save(any(TravelCompany.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(companyUserRepository.save(any(CompanyUser.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(companyVerificationRepository.save(any(CompanyVerification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ApplyCompanyRequest validApplication() {
        return new ApplyCompanyRequest(
                "Summit Travel Co.", "Summit Travel Private Limited", "REG-12345", "GST-999",
                "support@summittravel.example", "+91-9999999999", "Full refund up to 7 days before departure.",
                List.of(new CompanyDocumentRef("business_registration", "storage://doc-1")));
    }

    @Test
    void applyThrowsWhenApplicantIsNotIdApproved() {
        when(userService.getSummary(userId)).thenReturn(
                new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.EMAIL));

        assertThatThrownBy(() -> companyService.apply(userId, validApplication())).isInstanceOf(ForbiddenException.class);
        verify(travelCompanyRepository, never()).save(any());
    }

    @Test
    void applyThrowsWhenRegistrationNumberAlreadyExists() {
        when(userService.getSummary(userId)).thenReturn(
                new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        when(travelCompanyRepository.existsByRegistrationNumber("REG-12345")).thenReturn(true);

        assertThatThrownBy(() -> companyService.apply(userId, validApplication())).isInstanceOf(ConflictException.class);
    }

    @Test
    void applySucceedsAndSeedsTheFoundingOwnerAndAnUnderReviewVerificationRow() {
        when(userService.getSummary(userId)).thenReturn(
                new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.ID_APPROVED));
        when(travelCompanyRepository.existsByRegistrationNumber("REG-12345")).thenReturn(false);

        var response = companyService.apply(userId, validApplication());

        assertThat(response.displayName()).isEqualTo("Summit Travel Co.");
        assertThat(response.status()).isEqualTo("APPLICATION_SUBMITTED");
        verify(companyUserRepository).save(argThatOwnerFor(userId));
        verify(companyVerificationRepository).save(any(CompanyVerification.class));
    }

    @Test
    void getMyVerificationStatusThrowsWhenCallerHasNoCompany() {
        when(companyUserRepository.findFirstByUserIdAndStatus(userId, "active")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getMyVerificationStatus(userId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listStaffThrowsWhenCallerIsNotOwner() {
        CompanyUser manager = CompanyUser.invite(companyId, userId, CompanyUserRole.MANAGER, UUID.randomUUID());
        when(companyUserRepository.findFirstByUserIdAndStatus(userId, "active")).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> companyService.listStaff(userId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inviteStaffThrowsMultiAdminNotEnabledWhenRequestingAnotherOwner() {
        CompanyUser owner = CompanyUser.owner(companyId, userId);
        when(companyUserRepository.findFirstByUserIdAndStatus(userId, "active")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> companyService.inviteStaff(userId, new InviteStaffRequest(UUID.randomUUID(), "owner")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MULTI_ADMIN_NOT_ENABLED");
        verify(companyUserRepository, never()).save(any(CompanyUser.class));
    }

    @Test
    void inviteStaffThrowsWhenTheUserIsAlreadyActiveStaff() {
        CompanyUser owner = CompanyUser.owner(companyId, userId);
        UUID newStaffId = UUID.randomUUID();
        when(companyUserRepository.findFirstByUserIdAndStatus(userId, "active")).thenReturn(Optional.of(owner));
        when(companyUserRepository.existsByCompanyIdAndUserIdAndStatus(companyId, newStaffId, "active")).thenReturn(true);

        assertThatThrownBy(() -> companyService.inviteStaff(userId, new InviteStaffRequest(newStaffId, "manager")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void inviteStaffSucceedsForAManagerRole() {
        CompanyUser owner = CompanyUser.owner(companyId, userId);
        UUID newStaffId = UUID.randomUUID();
        when(companyUserRepository.findFirstByUserIdAndStatus(userId, "active")).thenReturn(Optional.of(owner));
        when(companyUserRepository.existsByCompanyIdAndUserIdAndStatus(companyId, newStaffId, "active")).thenReturn(false);

        var response = companyService.inviteStaff(userId, new InviteStaffRequest(newStaffId, "manager"));

        assertThat(response.role()).isEqualTo("MANAGER");
        assertThat(response.userId()).isEqualTo(newStaffId);
    }

    @Test
    void assertActiveMemberThrowsWhenTheUserIsNotAnActiveStaffMember() {
        when(companyUserRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "active")).thenReturn(false);

        assertThatThrownBy(() -> companyService.assertActiveMember(companyId, userId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertVerifiedThrowsWhenTheCompanyIsNotYetVerified() {
        ReflectionTestUtils.setField(companyService, "enforceCompanyVerification", true);
        TravelCompany company = TravelCompany.apply("Summit", "Summit Pvt Ltd", "REG-1", null, "a@b.com", "+911234567890", "policy");
        when(travelCompanyRepository.findById(companyId)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyService.assertVerified(companyId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertVerifiedIsANoOpWhenTheDevBypassIsDisabled() {
        ReflectionTestUtils.setField(companyService, "enforceCompanyVerification", false);

        companyService.assertVerified(companyId);

        verify(travelCompanyRepository, never()).findById(any());
    }

    private static CompanyUser argThatOwnerFor(UUID expectedUserId) {
        return org.mockito.ArgumentMatchers.argThat(cu -> cu.getUserId().equals(expectedUserId) && cu.getRole() == CompanyUserRole.OWNER);
    }
}
