# Use a lightweight Java 21 JDK image
FROM eclipse-temurin:21-jdk-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy the Gradle build files and source code
COPY build.gradle settings.gradle ./ 
COPY src ./src

# Install Gradle (if not already present in base image)
RUN apt-get update && apt-get install -y gradle

# Build the application using Gradle
# The -x test flag is used to skip running tests during the build process inside the Dockerfile.
# This is common practice for faster image builds, as tests can be run separately.
RUN gradle clean build -x test

# Expose the port the Spring Boot application runs on
EXPOSE 8080

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "build/libs/iam-system-1.0.0.jar"]