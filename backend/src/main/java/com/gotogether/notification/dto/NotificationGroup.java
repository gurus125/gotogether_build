package com.gotogether.notification.dto;

import java.util.List;

/**
 * One time-bucket of {@code GET /notifications}' "Paginated, pre-grouped"
 * response (API Spec Section 13, Chapter 1 Section 18) — {@code label} is
 * one of {@code TODAY}/{@code EARLIER_THIS_WEEK}/{@code OLDER}, matching the
 * approved Notifications screen's grouping. A group with no items for the
 * current page is omitted entirely rather than sent empty (mirrors the
 * mockup's own note: "a section header only renders when it has items").
 */
public record NotificationGroup(String label, List<NotificationResponse> items) {
}
