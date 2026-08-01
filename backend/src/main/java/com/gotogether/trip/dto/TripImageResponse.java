package com.gotogether.trip.dto;

import java.util.UUID;

public record TripImageResponse(UUID id, String imageUrl, short displayOrder, boolean primary) {}
