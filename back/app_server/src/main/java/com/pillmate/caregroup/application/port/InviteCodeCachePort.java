package com.pillmate.caregroup.application.port;

import java.time.Duration;
import java.util.Optional;

public interface InviteCodeCachePort {

    void put(String code, Long groupId, Duration ttl);

    Optional<Long> findGroupId(String code);
}
