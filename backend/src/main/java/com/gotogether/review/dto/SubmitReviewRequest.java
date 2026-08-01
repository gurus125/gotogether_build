package com.gotogether.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code POST /trips/{id}/reviews} (API Spec Section 11). All six sub-scores and {@code overall_rating} are required (Section 20: "Integer 1-5, all required"); {@code comment} is optional, capped at 280 chars (DB constraint + Section 20). */
public record SubmitReviewRequest(
        @NotNull UUID revieweeId,
        @NotNull @Min(1) @Max(5) Short ratingBehaviour,
        @NotNull @Min(1) @Max(5) Short ratingPunctuality,
        @NotNull @Min(1) @Max(5) Short ratingCommunication,
        @NotNull @Min(1) @Max(5) Short ratingCooperation,
        @NotNull @Min(1) @Max(5) Short ratingSafety,
        @NotNull @Min(1) @Max(5) Short ratingReliability,
        @NotNull @Min(1) @Max(5) Short overallRating,
        @Size(max = 280) String comment) {
}
