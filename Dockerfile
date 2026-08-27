# syntax=docker/dockerfile:1.7

ARG JAVA_BUILD_IMAGE=eclipse-temurin:21.0.11_10-jdk-alpine-3.23@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

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
