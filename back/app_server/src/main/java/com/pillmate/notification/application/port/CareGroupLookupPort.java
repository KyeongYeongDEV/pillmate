package com.pillmate.notification.application.port;

import java.util.Optional;

public interface CareGroupLookupPort {
    Optional<String> findNameById(Long careGroupId);
}
