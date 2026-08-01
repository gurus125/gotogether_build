package com.gotogether.notification.dto;

import java.util.List;

/** {@code GET /notifications}'s response envelope — grouped rather than the flat {@code CursorPageResponse<T>} used everywhere else, since the API Specification documents this endpoint's response as "Paginated, pre-grouped" specifically. */
public record NotificationsPageResponse(List<NotificationGroup> groups, String nextCursor, boolean hasMore) {
}
