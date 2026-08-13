package com.masiton.ai.application.port.out;

public interface YoutubeChannelWatchSubscriptionPort {

    void subscribe(String channelId, String verificationToken);
}
