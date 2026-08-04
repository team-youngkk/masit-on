package com.masiton.member.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface MemberParticipationUnlinkPort {
    void unlinkMemberParticipation(UUID memberId, OffsetDateTime unlinkedAt);
}
