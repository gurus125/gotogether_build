package com.gotogether.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.user.dto.SubmitVerificationRequest;
import com.gotogether.user.entity.User;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.entity.VerificationType;
import com.gotogether.user.repository.UserRepository;
import com.gotogether.user.repository.VerificationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationRepository verificationRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, verificationRepository);
    }

    @Test
    void recordAutoVerificationBumpsLevelFromNoneToPhone() {
        User user = User.newPhoneUser("+919999999999");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.recordAutoVerification(user, VerificationType.PHONE);

        assertThat(user.getVerificationLevel()).isEqualTo(VerificationLevel.PHONE);
        verify(verificationRepository).save(any());
    }

    @Test
    void recordAutoVerificationBumpsLevelFromPhoneToEmailBecauseEmailRanksHigher() {
        User user = User.newGoogleUser("g-1", "traveller@example.com");
        user.setVerificationLevel(VerificationLevel.PHONE);

        userService.recordAutoVerification(user, VerificationType.EMAIL);

        assertThat(user.getVerificationLevel()).isEqualTo(VerificationLevel.EMAIL);
    }

    @Test
    void recordAutoVerificationNeverDowngradesAnAlreadyHigherLevel() {
        User user = User.newGoogleUser("g-2", "traveller2@example.com");
        user.setVerificationLevel(VerificationLevel.ID_APPROVED);

        userService.recordAutoVerification(user, VerificationType.PHONE);

        assertThat(user.getVerificationLevel()).isEqualTo(VerificationLevel.ID_APPROVED);
    }

    @Test
    void softDeleteMarksTheUserDeletedAndSuspended() {
        UUID userId = UUID.randomUUID();
        User user = User.newPhoneUser("+919999999999");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.softDelete(userId);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getStatus().name()).isEqualTo("SUSPENDED");
        verify(userRepository).save(user);
    }

    @Test
    void softDeleteThrowsWhenTheUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.softDelete(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitVerificationPersistsAPendingRecordForGovernmentId() {
        UUID userId = UUID.randomUUID();
        User user = User.newGoogleUser("g-3", "traveller3@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.submitVerification(
                userId, new SubmitVerificationRequest(VerificationType.GOVERNMENT_ID, "aadhaar", "https://example.com/doc.jpg"));

        assertThat(response.type()).isEqualTo(VerificationType.GOVERNMENT_ID);
        assertThat(response.status().name()).isEqualTo("PENDING");
    }

    @Test
    void getSummaryThrowsWhenTheUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getSummary(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
