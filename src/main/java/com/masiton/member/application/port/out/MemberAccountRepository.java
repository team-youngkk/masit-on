package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.masiton.member.domain.model.MemberAccount;

public interface MemberAccountRepository {
    Optional<MemberAccount> findByEmail(String email);
    Optional<MemberAccount> findByEmailForUpdate(String email);
    Optional<MemberAccount> findById(UUID id);
    Optional<MemberAccount> findByIdForUpdate(UUID id);
    MemberAccount create(String email, String passwordHash, Instant now);
    Optional<MemberAccount> createIfAbsent(String email, String passwordHash, Instant now);
    void activate(UUID id, Instant verifiedAt);
    void changePassword(UUID id, String passwordHash, Instant now);
    void requestDeletion(UUID id, Instant now);
}
