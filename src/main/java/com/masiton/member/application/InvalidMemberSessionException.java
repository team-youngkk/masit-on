package com.masiton.member.application;

import java.util.Set;

public class InvalidMemberSessionException extends RuntimeException {

    private final Set<String> revokedSessionIds;

    public InvalidMemberSessionException() {
        this(Set.of());
    }

    public InvalidMemberSessionException(Set<String> revokedSessionIds) {
        this.revokedSessionIds = Set.copyOf(revokedSessionIds);
    }

    public Set<String> revokedSessionIds() {
        return revokedSessionIds;
    }
}
