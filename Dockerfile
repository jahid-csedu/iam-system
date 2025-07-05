# Build Stage
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle jacoco_excludes.gradle ./

# Copy source code
COPY src ./src

# Make gradlew executable
RUN chmod +x gradlew

# Build the application
# Use --no-daemon for CI/CD environments
# -x test to skip tests during build
RUN ./gradlew clean build -x test

# Run Stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the port the Spring Boot application runs on
EXPOSE 8080

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
