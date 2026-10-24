# Stage 1: Build the Spring Boot + H2 JAR with Gradle
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

# Stage 2: Run the Spring Boot + H2 Server with persistent /app/data volume
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data
VOLUME ["/app/data"]
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENV PORT=8080
ENV DB_URL="jdbc:h2:file:/app/data/retailflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
