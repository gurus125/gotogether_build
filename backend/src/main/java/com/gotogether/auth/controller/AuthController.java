package com.gotogether.auth.controller;

import com.gotogether.auth.dto.AuthResponse;
import com.gotogether.auth.dto.GoogleSignInRequest;
import com.gotogether.auth.dto.PhoneOtpRequestRequest;
import com.gotogether.auth.dto.PhoneOtpVerifyRequest;
import com.gotogether.auth.dto.RefreshRequest;
import com.gotogether.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public AuthResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        return authService.signInWithGoogle(request);
    }

    @PostMapping("/phone/otp/request")
    public ResponseEntity<Void> requestPhoneOtp(@Valid @RequestBody PhoneOtpRequestRequest request) {
        authService.requestPhoneOtp(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/phone/otp/verify")
    public AuthResponse verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        return authService.verifyPhoneOtp(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
