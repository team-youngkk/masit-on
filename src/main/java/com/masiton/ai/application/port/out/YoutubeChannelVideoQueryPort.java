package com.masiton.ai.application.port.out;

import java.util.List;

public interface YoutubeChannelVideoQueryPort {

    VideoPage query(String channelId, String pageToken);

    record VideoPage(List<String> videoIds, String nextPageToken) { }
}
