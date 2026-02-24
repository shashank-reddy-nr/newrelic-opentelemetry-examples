# ── Stage 1: Build the application ──────────────────────────────────────────
# Amazon Corretto 21 Alpine: AWS-patched JDK 21 LTS with minimal Alpine base.
# This stage is discarded after build — only the compiled JAR is kept.
FROM amazoncorretto:21-alpine AS builder
ARG GRADLE_VERSION=8.12.1
RUN apk add --no-cache curl unzip && \
    curl -fSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    mv "/opt/gradle-${GRADLE_VERSION}" /opt/gradle && \
    rm /tmp/gradle.zip && \
    apk del curl unzip
ENV PATH="/opt/gradle/bin:${PATH}"

WORKDIR /build

# Pre-download dependencies (cached as a separate Docker layer)
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q

# Build the fat JAR
COPY src ./src
RUN gradle shadowJar --no-daemon -q

# ── Stage 2: Download OpenTelemetry Java Agent ───────────────────────────────
FROM alpine:3.20 AS agent-downloader
RUN apk add --no-cache curl && \
    curl -fSL \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar" \
      -o /opentelemetry-javaagent.jar

# ── Stage 3: Runtime ─────────────────────────────────────────────────────────
# Google distroless: contains only the JRE + your app.
# No shell, no package manager, no extra OS packages — minimal CVE surface.
FROM gcr.io/distroless/java21-debian12
WORKDIR /app

COPY --from=builder /build/build/libs/app.jar ./app.jar
COPY --from=agent-downloader /opentelemetry-javaagent.jar ./opentelemetry-javaagent.jar

# Attach the OpenTelemetry Java Agent with Kafka instrumentation flags.
# These enable consumer span creation and W3C Trace Context extraction
# from message headers to link consumer spans back to producer spans.
# See: https://docs.newrelic.com/docs/opentelemetry/integrations/kafka/self-hosted/#instrument-apps
#
# OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_SERVICE_NAME, OTEL_RESOURCE_ATTRIBUTES,
# OTEL_METRICS_EXPORTER, OTEL_TRACES_EXPORTER are set at runtime via docker-compose.
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar -Dotel.instrumentation.kafka.experimental-span-attributes=true -Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true -Dotel.instrumentation.kafka.producer-propagation.enabled=true -Dotel.instrumentation.kafka.enabled=true"

CMD ["/app/app.jar"]
