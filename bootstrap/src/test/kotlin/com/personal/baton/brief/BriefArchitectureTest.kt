package com.personal.baton.brief

import com.personal.baton.brief.application.BriefPersistencePort
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

class BriefArchitectureTest {
    @Test
    fun `모듈은 안쪽 계층에만 의존하고 어댑터끼리 참조하지 않는다`() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("$BASE.domain..")
            .layer("Application").definedBy("$BASE.application..")
            .layer("Web").definedBy("$BASE.web..")
            .layer("Persistence").definedBy("$BASE.persistence..")
            .layer("Bootstrap").definedBy(BASE, "$BASE.config..")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Persistence", "Bootstrap")
            .whereLayer("Web").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
            .ensureAllClassesAreContainedInArchitecture()
            .check(productionClasses)
    }

    @Test
    fun `도메인과 유스케이스는 프레임워크에 의존하지 않는다`() {
        classes().that().resideInAnyPackage("$BASE.domain..", "$BASE.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "$BASE.domain..", "$BASE.application..", "java..", "kotlin..", "org.jetbrains.annotations..",
            )
            .check(productionClasses)
    }

    @Test
    fun `웹 어댑터는 저장 포트를 직접 사용하지 않는다`() {
        noClasses().that().resideInAPackage("$BASE.web..")
            .should().dependOnClassesThat().areAssignableTo(BriefPersistencePort::class.java)
            .check(productionClasses)
    }

    @Test
    fun `SQL과 저장 프레임워크는 영속성 어댑터와 구성 루트에서만 사용한다`() {
        noClasses().that().resideInAnyPackage("$BASE.domain..", "$BASE.application..", "$BASE.web..")
            .should().dependOnClassesThat(
                resideInAnyPackage(
                    "java.sql..", "javax.sql..", "org.springframework.jdbc..", "org.springframework.transaction..",
                    "org.flywaydb..", "org.hibernate..", "jakarta.persistence..",
                ).and(resideOutsideOfPackage("org.hibernate.validator..")),
            )
            .check(productionClasses)
    }

    private companion object {
        const val BASE = "com.personal.baton.brief"
        val productionClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE)
    }
}
