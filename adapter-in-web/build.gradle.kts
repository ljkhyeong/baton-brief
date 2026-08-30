plugins {
    kotlin("jvm")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":application"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("io.micrometer:micrometer-core")
    implementation("tools.jackson.module:jackson-module-kotlin")
}
