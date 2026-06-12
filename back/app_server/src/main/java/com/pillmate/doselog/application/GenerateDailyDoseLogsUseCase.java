package com.pillmate.doselog.application;

import java.time.LocalDate;

public interface GenerateDailyDoseLogsUseCase {
    int generate(LocalDate date);
}
