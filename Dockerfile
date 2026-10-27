# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test -x spotlessCheck

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home appuser
RUN mkdir -p /app/data && chown appuser /app/data
COPY --from=build /app/build/libs/app.jar app.jar
ENV H2_DATABASE_PATH=/var/data/retailflow \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# Prepare the mounted disk as root, then run Java as the unprivileged user.
ENTRYPOINT ["sh", "-c", "mkdir -p /var/data && chown appuser /var/data && exec runuser -u appuser -- java $JAVA_OPTS -jar /app/app.jar"]
