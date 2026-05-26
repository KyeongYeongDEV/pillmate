package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.VerifyAliasResponse;

public interface VerifyAliasUseCase {
    VerifyAliasResponse verify(Long aliasId);
}
