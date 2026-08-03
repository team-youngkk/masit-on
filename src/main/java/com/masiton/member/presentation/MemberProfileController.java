package com.masiton.member.presentation;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorResponse;
import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.domain.model.MemberAccount;

@RestController
@RequestMapping("/api/me")
public class MemberProfileController {
    private static final String PRIVATE_NO_STORE = "private, no-store";

    private final MemberAuthenticationService service;

    public MemberProfileController(MemberAuthenticationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MemberResponse> current(JwtAuthenticationToken authentication) {
        return privateNoStore(ResponseEntity.ok())
                .body(response(service.currentMember(authentication.getName())));
    }

    @DeleteMapping
    public ResponseEntity<Void> requestDeletion(JwtAuthenticationToken authentication) {
        String sessionId = authentication.getToken().getClaimAsString("sid");
        service.requestDeletion(new MemberPrincipal(authentication.getName(), sessionId), authentication.getToken().getExpiresAt());
        return privateNoStore(ResponseEntity.accepted()).build();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        return privateNoStore(ResponseEntity.status(exception.status()))
                .body(ErrorResponse.of(
                        exception.code(),
                        exception.getMessage(),
                        exception.fieldErrors(),
                        traceId(request)));
    }

    private MemberResponse response(MemberAccount account) {
        return new MemberResponse(account.id().toString(), account.email());
    }

    private ResponseEntity.BodyBuilder privateNoStore(ResponseEntity.BodyBuilder response) {
        return response.header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE);
    }

    private String traceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (traceId != null) {
            return traceId;
        }
        Object requestTraceId = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        if (requestTraceId instanceof String value) {
            return value;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record MemberResponse(String id, String email) { }
}
