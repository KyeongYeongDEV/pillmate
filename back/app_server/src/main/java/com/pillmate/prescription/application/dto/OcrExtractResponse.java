package com.pillmate.prescription.application.dto;

import java.util.List;

public record OcrExtractResponse(List<ExtractedDrugItem> items) {}
