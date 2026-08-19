package com.masiton.common.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * application.yml(공통 계층)이 운영 불변값을 선언하고, application-*.yml(프로파일 계층)은
 * 그 값을 재정의하거나 상속 대상 설정을 다시 선언하지 않는지 검증한다. 스프링 컨텍스트를
 * 띄우지 않고 YAML 파일 자체를 읽어 계층 구조 결함을 조기에 잡는다.
 */
@DisplayName("설정 계층화")
class ConfigurationLayeringTest {

    private static final String COMMON_RESOURCE_NAME = "application.yml";
    private static final String PROFILE_RESOURCE_GLOB = "application-*.yml";
    private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

    @Test
    @DisplayName("공통 계층은 open-in-view와 ddl-auto 운영 불변값을 선언한다")
    void 공통설정_로드_운영불변값을선언한다() throws Exception {
        // given
        Path commonFile = commonResourceFile();

        // when
        PropertySource<?> common = load(commonFile);

        // then
        assertThat(common.getProperty("spring.jpa.open-in-view")).isEqualTo(false);
        assertThat(common.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    @DisplayName("공통 계층은 Refresh 쿠키와 관리자 경로 운영 불변값을 선언한다")
    void 공통설정_로드_인증쿠키불변값을선언한다() throws Exception {
        // given
        Path commonFile = commonResourceFile();

        // when
        PropertySource<?> common = load(commonFile);

        // then
        assertThat(common.getProperty("masiton.security.secure")).isEqualTo(true);
        assertThat(common.getProperty("masiton.security.same-site")).isEqualTo("Strict");
        assertThat(common.getProperty("masiton.security.path")).isEqualTo("/api/auth/tokens");
    }

    @Test
    @DisplayName("모든 프로파일 계층은 운영 불변값을 재정의하지 않는다")
    void 프로파일설정_모두로드_운영불변값을재정의하지않는다() throws Exception {
        // given
        List<Path> profileFiles = discoverProfileResourceFiles();
        assertThat(profileFiles).as("검증할 프로파일 설정 파일을 찾지 못했다").isNotEmpty();

        // when & then
        for (Path profileFile : profileFiles) {
            PropertySource<?> profile = load(profileFile);

            assertInvariantNotViolated(profileFile, profile, "spring.jpa.open-in-view", false);
            assertInvariantNotViolated(profileFile, profile, "spring.jpa.hibernate.ddl-auto", "validate");
            // RV-NFR-007: Refresh 쿠키는 어떤 환경에서도 Secure·SameSite=Strict·관리자 경로 한정이다.
            assertInvariantNotViolated(profileFile, profile, "masiton.security.secure", true);
            assertInvariantNotViolated(profileFile, profile, "masiton.security.same-site", "Strict");
            assertInvariantNotViolated(profileFile, profile, "masiton.security.path", "/api/auth/tokens");
        }
    }

    @Test
    @DisplayName("운영 리소스의 프로파일 계층은 JWT 키 재료를 담지 않는다")
    void 운영프로파일설정_모두로드_JWT키재료를담지않는다() throws Exception {
        // given: 테스트 리소스의 픽스처는 대상이 아니고, 배포 산출물에 들어가는 main 리소스만 검사한다.
        List<Path> mainProfileFiles = discoverProfileResourceFiles().stream()
                .filter(ConfigurationLayeringTest::isMainResource)
                .toList();
        assertThat(mainProfileFiles).as("검증할 운영 프로파일 설정 파일을 찾지 못했다").isNotEmpty();

        // when & then
        for (Path profileFile : mainProfileFiles) {
            EnumerablePropertySource<?> profile = (EnumerablePropertySource<?>) load(profileFile);

            assertThat(profile.getPropertyNames())
                    .as("%s 파일이 JWT 키 재료를 직접 선언했다. 키는 환경 변수로만 주입한다", profileFile.getFileName())
                    .noneMatch(name -> name.equals("masiton.security.jwt.private-key-pem"))
                    .noneMatch(name -> name.equals("masiton.security.jwt.public-key-pem"));
        }
    }

    @Test
    @DisplayName("프로파일 계층은 공통 계층에서 상속해야 할 management와 jpa 설정을 다시 선언하지 않는다")
    void 프로파일설정_모두로드_상속대상키를재선언하지않는다() throws Exception {
        // given
        List<Path> profileFiles = discoverProfileResourceFiles();
        assertThat(profileFiles).as("검증할 프로파일 설정 파일을 찾지 못했다").isNotEmpty();

        // when & then
        for (Path profileFile : profileFiles) {
            EnumerablePropertySource<?> profile = (EnumerablePropertySource<?>) load(profileFile);

            assertThat(profile.getPropertyNames())
                    .as("%s 파일이 공통 계층에서 상속해야 할 키를 재선언했다", profileFile.getFileName())
                    .noneMatch(name -> name.startsWith("management."))
                    .noneMatch(name -> name.startsWith("spring.jpa."));
        }
    }

    @Test
    @DisplayName("open-in-view를 true로 재정의한 프로파일은 위반으로 판정된다")
    void 경계값_openInView를true로재정의_위반으로판정된다() throws Exception {
        // given
        Resource violatingProfile = new ByteArrayResource(
                "spring:\n  jpa:\n    open-in-view: true\n".getBytes(StandardCharsets.UTF_8));
        PropertySource<?> loaded = load(violatingProfile, "future-violating-profile.yml");

        // when & then
        assertThatThrownBy(() ->
                assertInvariantNotViolated(Paths.get("future-violating-profile.yml"), loaded, "spring.jpa.open-in-view", false))
                .isInstanceOf(AssertionError.class);
    }

    private static void assertInvariantNotViolated(Path file, PropertySource<?> profile, String key, Object invariantValue) {
        Object actual = profile.getProperty(key);
        if (actual == null) {
            return;
        }
        assertThat(actual)
                .as("%s 파일에서 %s가 운영 불변값과 다른 값(%s)으로 재정의됐다", file.getFileName(), key, actual)
                .isEqualTo(invariantValue);
    }

    private static PropertySource<?> load(Path file) throws IOException {
        return load(new FileSystemResource(file), file.getFileName().toString());
    }

    private static PropertySource<?> load(Resource resource, String name) throws IOException {
        List<PropertySource<?>> sources = LOADER.load(name, resource);
        assertThat(sources).as("%s는 정확히 하나의 YAML 문서여야 한다", name).hasSize(1);
        return sources.get(0);
    }

    /** Gradle 리소스 출력 레이아웃에서 main 리소스 루트(build/resources/main) 소속인지 판별한다. */
    private static boolean isMainResource(Path file) {
        Path parent = file.getParent();
        return parent != null && "main".equals(parent.getFileName().toString());
    }

    private static Path commonResourceFile() throws URISyntaxException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(COMMON_RESOURCE_NAME);
        assertThat(url).as("공통 설정 파일(%s)을 classpath에서 찾지 못했다", COMMON_RESOURCE_NAME).isNotNull();
        return Paths.get(url.toURI());
    }

    /**
     * application.yml이 위치한 리소스 루트(예: build/resources/main)의 형제 리소스 루트들
     * (build/resources/test 등)까지 훑어 application-*.yml을 모두 찾는다. 특정 프로파일 이름을
     * 하드코딩하지 않으므로 새 프로파일 파일이 추가돼도 자동으로 검증 대상에 포함된다.
     */
    private static List<Path> discoverProfileResourceFiles() throws IOException, URISyntaxException {
        Path mainResourcesRoot = commonResourceFile().getParent();
        Path resourcesRoot = mainResourcesRoot.getParent();
        List<Path> profileFiles = new ArrayList<>();

        try (DirectoryStream<Path> resourceRoots = Files.newDirectoryStream(resourcesRoot)) {
            for (Path root : resourceRoots) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (DirectoryStream<Path> candidates = Files.newDirectoryStream(root, PROFILE_RESOURCE_GLOB)) {
                    for (Path candidate : candidates) {
                        profileFiles.add(candidate);
                    }
                }
            }
        }
        return profileFiles;
    }
}
