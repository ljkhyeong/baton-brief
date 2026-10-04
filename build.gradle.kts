import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    // Spring Boot Gradle 플러그인이 쓰는 라이브러리도 보안 수정 버전에 맞춘다. 실행 JAR에는 포함되지 않는다.
    dependencies {
        classpath(platform(libs.jackson.bom))
        constraints {
            classpath("org.apache.commons:commons-lang3:${libs.versions.commons.lang3.get()}")
        }
    }
}

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
        dependencies {
            add("implementation", platform(libs.kotlin.bom))
            // Spring Boot BOM에 보안 수정 버전이 반영되기 전까지 모든 모듈·구성의 버전을 맞춘다.
            add("implementation", platform(libs.jackson.bom))
            constraints {
                listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach { module ->
                    add("implementation", "org.apache.tomcat.embed:$module:${libs.versions.tomcat.get()}")
                }
            }
        }

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
    from(layout.projectDirectory.dir("docs/PRD")) {
        include(
            "0002_mvp-contract/spec.md",
            "0007_event-receipt-query/spec.md",
            "0018_baton-producer-compatibility/spec.md",
            "0019_baton-continuity-event-v2/spec.md",
        )
        into("docs/PRD")
    }
}
