package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class RegistrationUnitJsonSupportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("같은 S3 단위 식당의 위치만 전달한다")
    void boundLocation_서로다른식당_자기단위위치만전달한다() throws Exception {
        // Given
        var fields = mapper.readTree("""
                {"registrationUnits":[
                  {"candidateUnitId":"a","restaurantName":{"value":"가식당"},"location":{"value":"서울 중구"}},
                  {"candidateUnitId":"b","restaurantName":{"value":"나식당"},"location":{"value":"부산 중구"}}]}
                """);
        // When / Then
        assertThat(RegistrationUnitJsonSupport.boundLocation(fields, 2, "b", "나식당").location()).isEqualTo("부산 중구");
        assertThat(RegistrationUnitJsonSupport.boundLocation(fields, 2, "a", "나식당").location()).isNull();
        assertThat(RegistrationUnitJsonSupport.boundLocation(fields, 1, "a", "나식당").location()).isNull();
    }

    @Test
    @DisplayName("legacy 전역 위치와 중복 식별자는 전달하지 않는다")
    void boundLocation_전역위치나중복식별자_차단한다() throws Exception {
        // Given
        var legacy = mapper.readTree("{\"location\":\"서울 중구\"}");
        var duplicate = mapper.readTree("""
                {"registrationUnits":[
                  {"candidateUnitId":"a","restaurantName":{"value":"가식당"},"location":{"value":"서울 중구"}},
                  {"candidateUnitId":"a","restaurantName":{"value":"나식당"}}]}
                """);
        // When / Then
        assertThat(RegistrationUnitJsonSupport.boundLocation(legacy, 1, "a", "가식당").location()).isNull();
        assertThat(RegistrationUnitJsonSupport.boundLocation(duplicate, 1, "a", "가식당").location()).isNull();
    }
}
