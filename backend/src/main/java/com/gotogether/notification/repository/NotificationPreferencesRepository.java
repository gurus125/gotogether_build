package com.gotogether.notification.repository;

import com.gotogether.notification.entity.NotificationPreferences;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
}
