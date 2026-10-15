package com.masiton.video.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.observability.ExternalFailureCauseClassifier;
import com.masiton.common.web.BusinessException;
import com.masiton.video.application.port.out.VideoVerificationPort;

class ResolveVerifiedVideoServiceTest {

    @Test
    @DisplayName("YouTube 검증 실패는 안전한 오류 코드로 변환하면서 진단 원인을 보존한다")
    void resolve_외부시간초과_오류코드와원인보존() {
        VideoVerificationPort port = mock(VideoVerificationPort.class);
        URI url = URI.create("https://www.youtube.com/watch?v=fixed-video");
        VideoVerificationFailedException providerFailure =
                new VideoVerificationFailedException(new HttpTimeoutException("secret-provider-message"));
        when(port.verify(url)).thenThrow(providerFailure);

        assertThatThrownBy(() -> new ResolveVerifiedVideoService(port).resolve(url))
                .isInstanceOfSatisfying(BusinessException.class, failure -> {
                    org.assertj.core.api.Assertions.assertThat(failure.code()).isEqualTo("EXTERNAL_SERVICE_ERROR");
                    org.assertj.core.api.Assertions.assertThat(failure.getCause()).isSameAs(providerFailure);
                    org.assertj.core.api.Assertions.assertThat(failure.getMessage()).doesNotContain("secret");
                    org.assertj.core.api.Assertions.assertThat(
                            ExternalFailureCauseClassifier.hasTimeoutCause(failure)).isTrue();
                });
    }
}
