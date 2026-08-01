package com.gotogether.storage.dto;

/**
 * One Pexels search result, trimmed to exactly what the mobile "search
 * photos" picker needs: something to show in a grid ({@code thumbnailUrl}),
 * something to actually download and upload if picked ({@code fullUrl}),
 * and photographer credit (Pexels' API terms ask for attribution when a
 * photo sourced through their API is used — {@code photographerName}/
 * {@code photographerUrl} let the mobile UI show it without a second
 * round-trip).
 */
public record PhotoSearchResultResponse(
        String id,
        String thumbnailUrl,
        String fullUrl,
        String photographerName,
        String photographerUrl) {
}
