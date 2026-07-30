package com.masiton.member.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.domain.model.MemberAccount;

@RestController
@RequestMapping("/api/me")
public class MemberProfileController {
    private final MemberAuthenticationService service;

    public MemberProfileController(MemberAuthenticationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MemberResponse> current(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(response(service.currentMember(authentication.getName())));
    }

    @DeleteMapping
    public ResponseEntity<Void> requestDeletion(JwtAuthenticationToken authentication) {
        String sessionId = authentication.getToken().getClaimAsString("sid");
        service.requestDeletion(new MemberPrincipal(authentication.getName(), sessionId), authentication.getToken().getExpiresAt());
        return ResponseEntity.accepted().build();
    }

    private MemberResponse response(MemberAccount account) {
        return new MemberResponse(account.id().toString(), account.email());
    }

    public record MemberResponse(String id, String email) { }
}
