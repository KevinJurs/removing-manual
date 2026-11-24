FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY rentaltracker/pom.xml .
RUN mvn dependency:resolve

COPY rentaltracker/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

COPY --from=build /build/target/rentaltracker.jar /app/rentaltracker.jar
WORKDIR /app/data

ENTRYPOINT [ "java", "-jar", "/app/rentaltracker.jar" ]
