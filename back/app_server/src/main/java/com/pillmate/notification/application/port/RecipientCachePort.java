package com.pillmate.notification.application.port;

import java.util.List;
import java.util.Optional;

public interface RecipientCachePort {

    Optional<List<CachedRecipient>> get(Long groupId);

    void put(Long groupId, List<CachedRecipient> recipients);

    void evict(Long groupId);

    record CachedRecipient(Long userId, String token) {}
}
