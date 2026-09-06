package com.pillmate.caregroup.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShareSettingRequest(@NotNull Boolean enabled) {}
