package com.masiton.member.application.port.in;

import java.util.UUID;

public interface LockActiveMemberUseCase {

    void lockActiveMember(UUID memberId);
}
