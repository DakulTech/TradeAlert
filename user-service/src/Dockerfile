# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app
ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2"

# Copy the POM first so dependency resolution remains cacheable.
COPY pom.xml .

# Copy source code and build
COPY src ./src
RUN for attempt in 1 2 3 4 5; do mvn -B -ntp clean package -Dmaven.test.skip=true && exit 0; echo "Maven package failed; retrying ($attempt/5)" >&2; done; exit 1

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/user-service-*.jar app.jar

# Expose service port
EXPOSE 8080

# Environment variables (override in deployment)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Healthcheck for container orchestration
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
