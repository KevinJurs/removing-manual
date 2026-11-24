FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY rentaltracker/pom.xml ./pom.xml
COPY rentaltracker/src ./src

RUN mvn -B clean package

FROM eclipse-temurin:21-jre-jammy

COPY --from=build /build/target/rentaltracker.jar /app/rentaltracker.jar

WORKDIR /data

ENTRYPOINT [ "java", "-jar", "/app/rentaltracker.jar" ]
