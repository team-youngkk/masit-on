package com.masiton.orchestration.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.masiton.orchestration.application.port.out.PlaceIdentityMatchingPolicy;

@ConfigurationProperties("masiton.ai.place-identity")
public class PlaceIdentityMatchingProperties implements PlaceIdentityMatchingPolicy {

    private boolean relaxedMatchingEnabled = true;

    @Override
    public boolean relaxedMatchingEnabled() {
        return relaxedMatchingEnabled;
    }

    public void setRelaxedMatchingEnabled(boolean relaxedMatchingEnabled) {
        this.relaxedMatchingEnabled = relaxedMatchingEnabled;
    }
}
