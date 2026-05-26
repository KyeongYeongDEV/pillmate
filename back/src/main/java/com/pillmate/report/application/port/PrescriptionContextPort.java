package com.pillmate.report.application.port;

import java.util.List;

public interface PrescriptionContextPort {

    PatientContext loadContext(Long patientId);

    record PatientContext(Long careGroupId, List<DrugSummary> drugs) {}

    record DrugSummary(String kdCode, String name, String efficacy) {}
}
