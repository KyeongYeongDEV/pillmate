package com.pillmate.drug.application.port;

import java.time.Duration;

public interface DrugImageStoragePort {
    String issueViewUrl(String objectKey, Duration ttl);
}
