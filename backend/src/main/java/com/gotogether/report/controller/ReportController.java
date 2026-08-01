package com.gotogether.report.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.report.dto.CreateReportRequest;
import com.gotogether.report.dto.ReportEvidenceResponse;
import com.gotogether.report.dto.ReportResponse;
import com.gotogether.report.dto.SubmitReportEvidenceRequest;
import com.gotogether.report.service.ReportService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Report APIs (API Specification Section 15). */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> file(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.file(principal.userId(), request));
    }

    @PostMapping("/reports/emergency")
    public ResponseEntity<ReportResponse> fileEmergency(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.fileEmergency(principal.userId(), request));
    }

    @PostMapping("/reports/{id}/evidence")
    public ResponseEntity<ReportEvidenceResponse> addEvidence(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SubmitReportEvidenceRequest request) {
        ReportEvidenceResponse response = reportService.addEvidence(
                principal.userId(), id, request.storageKey(), request.mimeType(), request.fileSizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
