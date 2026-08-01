package com.gotogether;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GoTogether backend entry point.
 *
 * <p>Modular monolith: each top-level package under {@code com.gotogether}
 * (auth, user, profile, destination, trip, joinrequest, membership, chat,
 * review, trust, notification, company, report, admin, analytics, storage,
 * common) is an independent module. The one architectural rule every module
 * must respect is enforced by {@link com.gotogether.ArchitectureTest} in the
 * test tree: a module's {@code repository} package is never imported from
 * outside that module — cross-module reads go through the owning module's
 * {@code service} package instead.
 *
 * <p>{@code @EnableScheduling} — required for {@code
 * TripLifecycleScheduler}'s {@code @Scheduled} job (trips auto-transitioning
 * to InProgress/Completed by date). Without this annotation Spring silently
 * never invokes {@code @Scheduled} methods at all — there's no error, no
 * warning, the job just never runs, which is exactly the class of bug that
 * left trip completion completely unreachable before this was added.
 */
@SpringBootApplication
@EnableScheduling
public class GoTogetherApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoTogetherApplication.class, args);
    }
}
