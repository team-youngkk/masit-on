package com.masiton.orchestration.presentation;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.orchestration.application.port.in.RegisterVisitRelationshipUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("방문 관계 등록 Controller API")
class VisitRelationshipRegistrationControllerApiTest {

    private final RegisterVisitRelationshipUseCase useCase = mock(RegisterVisitRelationshipUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new VisitRelationshipRegistrationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("유효한 관리자 요청은 201과 관계 식별자를 반환한다")
    void register_유효한요청_201과관계를반환한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        when(useCase.register(any(), any())).thenReturn(new RegisterVisitRelationshipUseCase.RegisteredVisitRelationship(
                visitId, restaurantId, creatorId, videoId));

        mockMvc.perform(post("/api/admin/visit-relationships")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin-id", "", java.util.List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(restaurantId, creatorId, videoId, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(visitId.toString()))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()));
    }

    @Test
    @DisplayName("누락된 식별자는 400 MISSING_REQUIRED_FIELD를 반환한다")
    void register_식별자누락_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/visit-relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":\"" + UUID.randomUUID() + "\",\"videoId\":\""
                                + UUID.randomUUID() + "\",\"visitEvidenceConfirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("잘못된 식별자는 400 INVALID_IDENTIFIER를 반환한다")
    void register_잘못된식별자_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/visit-relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":\"bad\",\"creatorId\":\"" + UUID.randomUUID()
                                + "\",\"videoId\":\"" + UUID.randomUUID()
                                + "\",\"visitEvidenceConfirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));
    }

    @Test
    @DisplayName("방문 근거 확인이 true가 아니면 422를 반환한다")
    void register_방문근거미확인_422을반환한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/visit-relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(restaurantId, creatorId, videoId, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_INSUFFICIENT"));
    }

    private String body(UUID restaurantId, UUID creatorId, UUID videoId, boolean evidence) {
        return "{\"restaurantId\":\"" + restaurantId + "\",\"creatorId\":\"" + creatorId
                + "\",\"videoId\":\"" + videoId + "\",\"visitEvidenceConfirmed\":" + evidence + "}";
    }
}
