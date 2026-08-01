package com.gotogether.user.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.user.dto.SubmitVerificationRequest;
import com.gotogether.user.dto.UserResponse;
import com.gotogether.user.dto.VerificationResponse;
import com.gotogether.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.userId());
    }

    @PostMapping("/deactivate")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivate(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reactivate")
    public ResponseEntity<Void> reactivate(@AuthenticationPrincipal UserPrincipal principal) {
        userService.reactivate(principal.userId());
        return ResponseEntity.noContent().build();
    }

    /** Soft-delete with a 30-day grace period (Business Rules Module 1 Section 10) — not an immediate hard delete. */
    @DeleteMapping
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal UserPrincipal principal) {
        userService.softDelete(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verifications")
    public ResponseEntity<VerificationResponse> submitVerification(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SubmitVerificationRequest request) {
        VerificationResponse response = userService.submitVerification(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/verifications")
    public List<VerificationResponse> listVerifications(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.listVerifications(principal.userId());
    }
}
