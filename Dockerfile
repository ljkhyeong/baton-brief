# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

# Dependabot은 ARG로 지정한 기반 이미지를 갱신하지 않으므로 FROM에 digest를 직접 고정한다.
FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.23@sha256:52ca1500049e2e2e7cee85fc4cd8689108792dc2bde18161b7f9236f609f0dea AS build
WORKDIR /workspace

COPY --chmod=0555 gradlew ./gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY domain/build.gradle.kts ./domain/build.gradle.kts
COPY application/build.gradle.kts ./application/build.gradle.kts
COPY adapter-in-web/build.gradle.kts ./adapter-in-web/build.gradle.kts
COPY adapter-out-persistence/build.gradle.kts ./adapter-out-persistence/build.gradle.kts
COPY bootstrap/build.gradle.kts ./bootstrap/build.gradle.kts

COPY domain/src/main ./domain/src/main
COPY application/src/main ./application/src/main
COPY adapter-in-web/src/main ./adapter-in-web/src/main
COPY adapter-out-persistence/src/main ./adapter-out-persistence/src/main
COPY bootstrap/src/main ./bootstrap/src/main

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :bootstrap:bootJar

FROM eclipse-temurin:21.0.12_8-jre-alpine-3.23@sha256:1f165f3a75613b80cc803a2ccc4d865e5c8b845b97f692988d75c4a026609e61 AS runtime

RUN command -v wget >/dev/null \
    && addgroup -S -g 10001 brief \
    && adduser -S -D -H -u 10001 -G brief brief

WORKDIR /opt/baton-brief
COPY --from=build --chown=10001:10001 --chmod=0444 \
    /workspace/bootstrap/build/libs/baton-brief.jar ./baton-brief.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/opt/baton-brief/baton-brief.jar"]
