FROM maven:3.9.16-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-noble

WORKDIR /app

COPY --from=build --chown=10001:0 /workspace/target/incidentops-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

USER 10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
