FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY . /app
RUN ./gradlew clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.32.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN chmod 644 /app/opentelemetry-javaagent.jar

COPY --from=build /app/build/libs/shipping-service-0.0.1-SNAPSHOT.jar /app/shipping-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "shipping-service.jar"]