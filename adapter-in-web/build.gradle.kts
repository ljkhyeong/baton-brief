plugins {
    kotlin("jvm")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":application"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // 정적 Bearer 검사에 resource server 필터만 쓰므로 JWT(jose) 모듈은 넣지 않는다.
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("io.micrometer:micrometer-core")
    implementation("tools.jackson.module:jackson-module-kotlin")
}
