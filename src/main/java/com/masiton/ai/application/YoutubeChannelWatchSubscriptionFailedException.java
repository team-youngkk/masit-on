package com.masiton.ai.application;

public class YoutubeChannelWatchSubscriptionFailedException extends RuntimeException {

    private final String category;

    public YoutubeChannelWatchSubscriptionFailedException(String category) {
        this(category, null);
    }

    public YoutubeChannelWatchSubscriptionFailedException(String category, Throwable cause) {
        super(cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
