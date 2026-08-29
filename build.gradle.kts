import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.personal.baton.brief"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

val contractsVersion = providers
    .fileContents(layout.projectDirectory.file("contracts/VERSION"))
    .asText
    .map(String::trim)

tasks.register<Zip>("contractsZip") {
    group = "distribution"
    description = "BATON BRIEF 이벤트 계약 팩 ZIP을 생성합니다."
    archiveFileName.set(contractsVersion.map { "baton-brief-contracts-$it.zip" })
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(layout.projectDirectory.dir("contracts")) {
        into("contracts")
    }
    from(layout.projectDirectory.file("docs/PRD/0019_baton-continuity-event-v2/spec.md")) {
        into("docs/PRD/0019_baton-continuity-event-v2")
    }
}
