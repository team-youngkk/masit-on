FROM eclipse-temurin:21.0.11_10-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21.0.11_10-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S masiton && adduser -S masiton -G masiton
COPY --from=build /workspace/build/libs/*.jar /app/application.jar
USER masiton

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
