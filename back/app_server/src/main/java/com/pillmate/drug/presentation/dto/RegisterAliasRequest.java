package com.pillmate.drug.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAliasRequest(
        @NotBlank @Size(max = 500) String nameRaw,
        @NotBlank @Size(max = 20) String itemSeq) {}
