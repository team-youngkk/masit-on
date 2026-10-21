# ADR-LANG-001 6·10절이 개발·CI·이미지 전 구간에 JDK 21.0.12를 요구한다.
#
# Eclipse Temurin은 21.0.12+8 Alpine 이미지를 배포하므로 CI의 setup-java와
# 같은 OpenJDK 기준을 사용할 수 있다. Alpine 3.22 variant와 digest를 함께 고정한다.
#
# `java -version`은 정확히 21.0.12를 보고한다. ADR-LANG-001은 벤더를 지정하지
# 않고 버전 문자열을 요구하므로 조문을 충족한다.
#
# 베이스 이미지는 명시 태그와 digest를 함께 고정한다 (ADR-RUNTIME-001 13절).
# 태그만 두면 같은 태그가 다른 이미지를 가리킬 수 있어 재현 빌드가 깨진다.
FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.22@sha256:d2e87dcac30074a92c1737d939587bb35c8e383d496d7d7e28176817ec605ba9 AS build
# 고정된 Temurin 이미지가 Alpine OpenSSL 보안 업데이트보다 먼저 만들어졌으므로,
# JDK·베이스 이미지 digest는 유지하고 수정된 패키지를 명시적으로 설치한다.
RUN apk add --no-cache libcrypto3=3.5.9-r0 libssl3=3.5.9-r0 expat=2.9.0-r0 libexpat=2.9.0-r0
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
# Windows 체크아웃 등 실행 비트가 보존되지 않는 컨텍스트에서도 빌드가 되도록 보장한다
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar

# 런타임도 같은 JDK 기준을 사용해 빌드·실행 환경의 버전을 일치시킨다.
FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.22@sha256:d2e87dcac30074a92c1737d939587bb35c8e383d496d7d7e28176817ec605ba9 AS runtime
# 런타임 이미지는 별도로 스캔하므로 build 단계와 같은 OpenSSL 패키지 버전을 고정한다.
RUN apk add --no-cache libcrypto3=3.5.9-r0 libssl3=3.5.9-r0 expat=2.9.0-r0 libexpat=2.9.0-r0
WORKDIR /app

# uid·gid를 고정해야 베이스 이미지가 바뀌어도 실행 사용자가 동일하다
RUN addgroup -S -g 1001 masiton && adduser -S -u 1001 -G masiton masiton
COPY --from=build /workspace/build/libs/*.jar /app/application.jar
USER masiton

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
