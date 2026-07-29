# 베이스 이미지는 명시 태그와 digest를 함께 고정한다 (ADR-RUNTIME-001 13절).
# 태그만 두면 같은 태그가 다른 이미지를 가리킬 수 있어 재현 빌드가 깨진다.
FROM eclipse-temurin:21.0.11_10-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
# Windows 체크아웃 등 실행 비트가 보존되지 않는 컨텍스트에서도 빌드가 되도록 보장한다
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21.0.11_10-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c AS runtime
WORKDIR /app

# uid·gid를 고정해야 베이스 이미지가 바뀌어도 실행 사용자가 동일하다
RUN addgroup -S -g 1001 masiton && adduser -S -u 1001 -G masiton masiton
COPY --from=build /workspace/build/libs/*.jar /app/application.jar
USER masiton

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
