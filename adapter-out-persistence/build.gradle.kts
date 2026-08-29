plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":application"))
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
