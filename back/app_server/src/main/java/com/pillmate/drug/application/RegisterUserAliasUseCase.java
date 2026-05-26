package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.RegisterUserAliasCommand;
import com.pillmate.drug.application.dto.RegisterUserAliasResponse;

public interface RegisterUserAliasUseCase {
    RegisterUserAliasResponse register(RegisterUserAliasCommand command);
}
