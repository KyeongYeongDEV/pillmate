package com.pillmate.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateColorRequest(@NotBlank String color) {}
