package com.pillmate.activity.application.port;

import com.pillmate.activity.application.dto.ActivityFeedItem;

import java.util.List;
import java.util.Optional;

public interface ActivityFeedCachePort {

    Optional<List<ActivityFeedItem>> getGroupFeed(Long groupId, Long viewerId, int limit);

    void putGroupFeed(Long groupId, Long viewerId, int limit, List<ActivityFeedItem> items);

    void evictGroup(Long groupId);
}
