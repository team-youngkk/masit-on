# ADR-LANG-001 6·10절이 개발·CI·이미지 전 구간에 JDK 21.0.12를 요구한다.
#
# eclipse-temurin은 21.0.12 컨테이너 이미지를 아직 배포하지 않는다(전체 variant 0건,
# 최신 alpine은 21.0.11_10). Adoptium tarball은 21.0.12+8이 있어 CI의 setup-java는
# 그것을 받으므로, temurin 이미지를 쓰면 CI와 이미지의 패치 버전이 갈린다.
#
# Amazon Corretto 21.0.12는 같은 업스트림 빌드(21.0.12+8)이고 `java -version`이
# 정확히 21.0.12를 보고한다. ADR-LANG-001은 벤더를 지정하지 않고 버전 문자열만
# 요구하므로 조문을 충족한다. temurin이 21.0.12를 배포하면 되돌린다.
#
# 베이스 이미지는 명시 태그와 digest를 함께 고정한다 (ADR-RUNTIME-001 13절).
# 태그만 두면 같은 태그가 다른 이미지를 가리킬 수 있어 재현 빌드가 깨진다.
FROM amazoncorretto:21.0.12-alpine@sha256:58c1d555f4ff3be0cfe90d3b4d1762bde080b57afbb71d48657b9d22748cad5b AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
# Windows 체크아웃 등 실행 비트가 보존되지 않는 컨텍스트에서도 빌드가 되도록 보장한다
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon clean bootJar

# Corretto는 JRE 전용 variant를 배포하지 않아 런타임도 JDK 이미지를 쓴다.
# temurin의 jre-alpine보다 이미지가 커지지만 ADR-LANG-001의 패치 일치가 우선이다.
FROM amazoncorretto:21.0.12-alpine@sha256:58c1d555f4ff3be0cfe90d3b4d1762bde080b57afbb71d48657b9d22748cad5b AS runtime
WORKDIR /app

# uid·gid를 고정해야 베이스 이미지가 바뀌어도 실행 사용자가 동일하다
RUN addgroup -S -g 1001 masiton && adduser -S -u 1001 -G masiton masiton
COPY --from=build /workspace/build/libs/*.jar /app/application.jar
USER masiton

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
