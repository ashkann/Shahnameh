# syntax=docker/dockerfile:1

FROM openjdk:8-jre-alpine

ADD backend/target/scala-2.13/backend-assembly-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app.jar"]