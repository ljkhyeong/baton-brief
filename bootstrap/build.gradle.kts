import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":application"))
    implementation(project(":adapter-in-web"))
    implementation(project(":adapter-out-persistence"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-jackson")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.networknt:json-schema-validator:3.0.6") {
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
    }
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        into("contracts")
    }
}

val architectureTest by tasks.registering(Test::class) {
    group = "verification"
    description = "DB 없이 모듈과 저장 포트의 의존 규칙을 검사합니다."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/BriefArchitectureTest.class")
}

tasks.named<Test>("test") {
    dependsOn(architectureTest)
    exclude("**/BriefArchitectureTest.class")
}
