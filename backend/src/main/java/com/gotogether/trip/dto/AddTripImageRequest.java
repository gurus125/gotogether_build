package com.gotogether.trip.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code imageUrl} is the {@code public_url} returned by the storage module's presigned-upload-url step, persisted here as a new {@code trip_images} row. */
public record AddTripImageRequest(@NotBlank String imageUrl, boolean isPrimary) {}
