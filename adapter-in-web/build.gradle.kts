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

    constraints {
        listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach { module ->
            implementation("org.apache.tomcat.embed:$module:${libs.versions.tomcat.get()}") {
                because("Spring Boot BOM에 반영되기 전 Tomcat 11.0.25 보안 수정 적용")
            }
        }
    }
}
