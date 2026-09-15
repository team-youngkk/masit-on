package com.masiton.ai.application.port.out;

import java.util.List;

public interface YoutubeChannelVideoQueryPort {

    default VideoPage query(String channelId, String pageToken) {
        return query(channelId, pageToken, 50);
    }

    VideoPage query(String channelId, String pageToken, int maxResults);

    record VideoPage(List<String> videoIds, String nextPageToken) { }
}
