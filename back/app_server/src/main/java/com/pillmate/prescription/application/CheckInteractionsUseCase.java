package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.InteractionWarning;

import java.util.List;

public interface CheckInteractionsUseCase {

    List<InteractionWarning> check(List<String> kdCodes);
}
