import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.0" apply false
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
