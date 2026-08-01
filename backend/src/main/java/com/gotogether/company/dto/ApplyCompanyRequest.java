package com.gotogether.company.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code POST /companies/apply} (API Specification Section 14). */
public record ApplyCompanyRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 200) String legalName,
        @NotBlank String registrationNumber,
        String gstNumber,
        @NotBlank @Email String supportEmail,
        @NotBlank String supportPhone,
        @NotBlank @Size(max = 2000) String cancellationPolicy,
        @NotNull @NotEmpty List<@Valid CompanyDocumentRef> documents) {
}
