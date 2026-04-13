FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src/main/java/ru/mentee/power/mcloud_9/TodoApp.java .
ADD https://jdbc.postgresql.org/download/postgresql-42.7.4.jar /src/lib/postgresql.jar
RUN javac -cp /src/lib/postgresql.jar -d out TodoApp.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/out/ .
COPY --from=build /src/lib/postgresql.jar /app/lib/
CMD ["java", "-cp", ".:/app/lib/postgresql.jar", "ru.mentee.power.mcloud_9.TodoApp"]
