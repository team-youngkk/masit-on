FROM eclipse-temurin:21.0.11_10-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
# Windows 체크아웃 등 실행 비트가 보존되지 않는 컨텍스트에서도 빌드가 되도록 보장한다
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21.0.11_10-jre-alpine AS runtime
WORKDIR /app

# uid·gid를 고정해야 베이스 이미지가 바뀌어도 실행 사용자가 동일하다
RUN addgroup -S -g 1001 masiton && adduser -S -u 1001 -G masiton masiton
COPY --from=build /workspace/build/libs/*.jar /app/application.jar
USER masiton

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
