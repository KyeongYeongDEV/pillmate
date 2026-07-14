package com.pillmate.caregroup.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {}
