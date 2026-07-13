package com.pillmate.notification.application.port;

import java.util.Optional;

public interface DrugNameLookupPort {

    Optional<String> findNameById(Long drugId);
}
