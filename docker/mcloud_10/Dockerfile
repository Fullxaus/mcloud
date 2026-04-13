FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src
COPY src/main/java/ru/mentee/power/mcloud_10/${SERVICE}.java .
RUN javac -d out ${SERVICE}.java

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /src/out/ .
ENV SERVICE_CLASS=ru.mentee.power.mcloud_10.${SERVICE}
CMD java -cp . $SERVICE_CLASS
