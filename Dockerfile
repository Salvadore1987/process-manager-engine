FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY core core
COPY rabbitmq-transport rabbitmq-transport
COPY spring-integration spring-integration
COPY rest-api rest-api
RUN chmod +x gradlew && ./gradlew :rest-api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/rest-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
