package com.masiton.member.application.port.out;

import com.masiton.member.domain.model.MemberActionPurpose;

public interface MemberActionTokenDeliveryPort {
    void send(String email, MemberActionPurpose purpose, String rawToken);
}
