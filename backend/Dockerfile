# Stage 1: build the fat JAR
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: minimal runtime image
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S satzwerk && adduser -S satzwerk -G satzwerk
COPY --from=builder /app/build/libs/*.jar app.jar
USER satzwerk
EXPOSE 8080
# JVM heap flags are set explicitly rather than relying solely on Java 21's
# default container-aware ergonomics (MaxRAMPercentage=25%), for predictable
# behavior. These defaults assume the container memory limit configured in
# docker-compose.yml's backend.deploy.resources.limits.memory (768M) — keep
# both in sync if either changes. Override via the JAVA_OPTS env var without
# rebuilding the image.
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xms256m -Xmx512m -XX:MaxMetaspaceSize=128m"
# Shell form (with `exec`) so `java` becomes PID 1 and receives SIGTERM
# directly for graceful shutdown, while still expanding $JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
