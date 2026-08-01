package com.gotogether.notification.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.notification.dto.NotificationPreferencesResponse;
import com.gotogether.notification.dto.NotificationsPageResponse;
import com.gotogether.notification.dto.UpdateNotificationPreferencesRequest;
import com.gotogether.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notification APIs (API Specification Section 13). */
@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    public NotificationController(NotificationService notificationService, AnalyticsService analyticsService) {
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/notifications")
    public NotificationsPageResponse list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return notificationService.list(principal.userId(), filter, cursor, limit);
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        notificationService.markRead(principal.userId(), id);
        analyticsService.record("notification_opened", principal.userId(), ReferencedEntityType.NOTIFICATIONS.tableName(), id, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/{id}/archive")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        notificationService.archive(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/notification-preferences")
    public NotificationPreferencesResponse preferences(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.getPreferences(principal.userId());
    }

    @PatchMapping("/users/me/notification-preferences")
    public NotificationPreferencesResponse updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody UpdateNotificationPreferencesRequest request) {
        return notificationService.updatePreferences(principal.userId(), request);
    }
}
