# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

ARG JAVA_BUILD_IMAGE=eclipse-temurin:21.0.12_8-jdk-alpine-3.23@sha256:bcc6da0b0efc6fb0e445173784dddb45d5f70afa8a9128430a63f79ef0154601
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine-3.23@sha256:319339a7fc9c7b59478cbed0340b6ba4944b45384a6eba3b0086856f4af08d8d

FROM ${JAVA_BUILD_IMAGE} AS build
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
    ./gradlew --no-daemon :bootstrap:bootJar \
    && find bootstrap/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
        -exec cp '{}' /workspace/baton-brief.jar \; \
    && test -s /workspace/baton-brief.jar

FROM ${JAVA_RUNTIME_IMAGE} AS runtime

RUN command -v wget >/dev/null \
    && addgroup -S -g 10001 brief \
    && adduser -S -D -H -u 10001 -G brief brief

WORKDIR /opt/baton-brief
COPY --from=build --chown=10001:10001 --chmod=0444 \
    /workspace/baton-brief.jar ./baton-brief.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/opt/baton-brief/baton-brief.jar"]
