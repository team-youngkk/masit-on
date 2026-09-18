package com.masiton.ai.application;

public class YoutubeChannelVideoQueryException extends RuntimeException {
    private final String category;
    public YoutubeChannelVideoQueryException(String category) { this.category = category; }
    public YoutubeChannelVideoQueryException(String category, Throwable cause) { super(cause); this.category = category; }
    public String category() { return category; }
}
