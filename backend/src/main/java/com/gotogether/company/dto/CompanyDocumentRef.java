package com.gotogether.company.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One entry of {@code POST /companies/apply}'s {@code documents: [...]} array
 * (business registration certificate, GST/business registration documents,
 * optional tourism/travel-trade licence — Operations Module A's "Required
 * documents" row). {@code storageKey} is an already-uploaded object-storage
 * reference — like every other "attach a file" flow in this app (e.g. trip
 * images, chat attachments), there is no upload endpoint in this module
 * itself; the client uploads first and passes the resulting key.
 */
public record CompanyDocumentRef(@NotBlank String documentType, @NotBlank String storageKey) {
}
