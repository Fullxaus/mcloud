# Сборка fat JAR (PostgreSQL JDBC внутри), образ для Kubernetes (mcloud_13).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /w
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew configurableCalculatorJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /w/build/libs/configurable-calculator.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
