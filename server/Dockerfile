# syntax=docker/dockerfile:1
# Build context is the repo root: docker build -f server/Dockerfile .

FROM gradle:9.7.1-jdk25 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY config config
COPY server server
# AGP needs an Android SDK, so build with a settings file that only knows :server.
RUN grep -vE '^include\(":(androidApp|composeApp|shared)"\)$' settings.gradle.kts > settings.trimmed \
    && mv settings.trimmed settings.gradle.kts
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon :server:installDist

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /src/server/build/install/server ./
USER app
EXPOSE 8080
ENTRYPOINT ["bin/server"]
