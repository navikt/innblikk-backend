# Stage 1: Build native image
FROM ghcr.io/graalvm/native-image-community:25.0.2 AS builder
RUN microdnf install -y findutils binutils && microdnf clean all
WORKDIR /build

# Cache Gradle distribution download (changes only on Gradle version upgrade)
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

# Cache dependencies (changes only when build files change)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon -PjavaVersion=25 || true

# Copy source and build native image
COPY src src
ARG CI
ENV CI=${CI}
RUN ./gradlew nativeCompile --no-daemon -PjavaVersion=25 \
    && strip build/native/nativeCompile/app

# Stage 2: Minimal runtime image (Chainguard wolfi-base, apk-managed glibc/zlib)
FROM cgr.dev/chainguard/wolfi-base@sha256:a31344ab2cb8618db84f535eec56f76f6178b142cb92cb2e48676cc2dcebea72
LABEL maintainer="team-researchops"
RUN apk update && apk add --no-cache glibc zlib && rm -rf /var/cache/apk/*
WORKDIR /app
COPY --from=builder /build/build/native/nativeCompile/app ./app
EXPOSE 8080
ENTRYPOINT ["./app"]
