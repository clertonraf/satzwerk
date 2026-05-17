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
ENTRYPOINT ["java", "-jar", "app.jar"]
