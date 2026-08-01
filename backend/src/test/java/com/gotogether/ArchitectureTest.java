package com.gotogether;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the one load-bearing rule of the modular monolith
 * (Backend Architecture doc): a module's {@code repository} package is only
 * ever accessed from within that same module. Cross-module data access must
 * go through the owning module's public {@code service} interface instead.
 *
 * <p>This is deliberately a compile-time-adjacent guardrail, not a code
 * review reminder — the kickoff report flagged "someone imports another
 * module's repository directly under deadline pressure" as the main
 * long-term risk to this architecture, so the check exists from Phase 0
 * rather than being added after the first violation is found in review.
 *
 * <p>Runs against the whole {@code com.gotogether} tree; add new modules to
 * the {@code MODULES} array below as they're scaffolded.
 */
class ArchitectureTest {

    private static final String[] MODULES = {
        "auth", "user", "profile", "destination", "trip", "joinrequest",
        "membership", "chat", "review", "trust", "notification", "company",
        "report", "admin", "analytics", "storage"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.gotogether");
    }

    @Test
    void repositoriesAreOnlyAccessedWithinTheirOwnModule() {
        for (String module : MODULES) {
            String repoPackage = "com.gotogether." + module + ".repository..";
            String ownModule = "com.gotogether." + module + "..";

            ArchRule rule = noClasses()
                    .that()
                    .resideOutsideOfPackage(ownModule)
                    .should()
                    .accessClassesThat()
                    .resideInAPackage(repoPackage)
                    .as("classes outside the '" + module + "' module must not access '" + module + "' repositories — "
                            + "go through " + module + ".service instead");

            rule.check(classes);
        }
    }

    @Test
    void entitiesAreOnlyAccessedWithinTheirOwnModule() {
        for (String module : MODULES) {
            String entityPackage = "com.gotogether." + module + ".entity..";
            String ownModule = "com.gotogether." + module + "..";

            ArchRule rule = noClasses()
                    .that()
                    .resideOutsideOfPackage(ownModule)
                    .should()
                    .accessClassesThat()
                    .resideInAPackage(entityPackage)
                    .as("classes outside the '" + module + "' module must not access '" + module + "' JPA entities — "
                            + "use " + module + ".dto types across module boundaries instead");

            rule.check(classes);
        }
    }

    @Test
    void controllersAreNotAccessedByOtherModules() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage("..controller..", "..config..");

        rule.check(classes);
    }
}
