package com.pillmate.prescription.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.InteractionWarning;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.RegisteredDrugItem;
import com.pillmate.prescription.application.dto.ScheduleSpec;
import com.pillmate.prescription.application.exception.EmptyPrescriptionItemsException;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.application.port.SchedulingPort;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.prescription.domain.model.InteractionSeverity;
import com.pillmate.prescription.domain.model.CandidateDecisionType;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPrescriptionService {

    private static final int DEFAULT_MEDICATION_DAYS = 30;

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;
    private final ObjectMapper objectMapper;
    private final CheckInteractionsUseCase checkInteractionsUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final SchedulingPort schedulingPort;

    @Transactional
    public RegisterPrescriptionResponse register(RegisterPrescriptionCommand command) {
        requireNonEmptyItems(command.items());

        Prescription prescription = Prescription.create(
                command.patientId(), command.imageKey(), command.prescribedAt());

        List<RegisteredDrugItem> registered = appendDrugsAndCollect(prescription, command.items());
        List<PrescribedDrugCandidate> candidates = buildCandidates(command.items(), registered);
        prescription.attachCandidates(candidates);
        prescription.markOcrDone();

        List<InteractionWarning> warnings = checkInteractionsUseCase.check(extractMatchedKdCodes(registered));
        if (hasCriticalWarning(warnings)) {
            prescription.markManualReview();
        }

        Prescription saved = prescriptionRepository.save(prescription);
        int unresolvedCount = candidates.size();
        log.info("PrescriptionRegistered prescriptionId={} ocrStatus={} itemCount={} unmatched={} unresolved={} ddiWarnings={}",
                saved.getId(), saved.getOcrStatus(), registered.size(),
                countUnmatched(registered), unresolvedCount, warnings.size());

        if (hasCriticalWarning(warnings)) {
            List<String> criticalMessages = extractCriticalMessages(warnings);
            eventPublisher.publishEvent(new DdiCriticalDetected(command.patientId(), saved.getId(), criticalMessages));
        }
        eventPublisher.publishEvent(new PrescriptionRegistered(command.patientId(), saved.getId()));

        List<SchedulingPort.ScheduledSlot> createdSchedules = createSchedules(command, saved.getId());
        return new RegisterPrescriptionResponse(
                saved.getId(), saved.getOcrStatus(), registered, unresolvedCount, warnings, createdSchedules);
    }

    private List<SchedulingPort.ScheduledSlot> createSchedules(RegisterPrescriptionCommand command, Long prescriptionId) {
        ScheduleSpec spec = command.scheduleSpec();
        if (spec == null) {
            return List.of();
        }
        LocalDate startDate = spec.startDate() != null ? spec.startDate() : command.prescribedAt();
        LocalDate endDate = spec.endDate() != null
                ? spec.endDate()
                : startDate.plusDays(medicationDays(command.items()) - 1);
        return schedulingPort.createForPrescription(new SchedulingPort.CreateScheduleCommand(
                spec.careGroupId(), command.patientId(), prescriptionId, command.patientId(),
                toSlotInputs(spec.slots()), startDate, endDate));
    }

    private List<SchedulingPort.SlotInput> toSlotInputs(List<ScheduleSpec.SlotInput> slots) {
        if (slots == null) {
            return null;
        }
        return slots.stream()
                .map(slot -> new SchedulingPort.SlotInput(slot.timeOfDay(), slot.customTime()))
                .toList();
    }

    private int medicationDays(List<DrugItem> items) {
        return items.stream()
                .map(DrugItem::durationDays)
                .filter(d -> d != null && d > 0)
                .max(Integer::compareTo)
                .orElse(DEFAULT_MEDICATION_DAYS);
    }

    private void requireNonEmptyItems(List<DrugItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyPrescriptionItemsException();
        }
    }

    private List<RegisteredDrugItem> appendDrugsAndCollect(
            Prescription prescription, List<DrugItem> items) {
        List<RegisteredDrugItem> registered = new ArrayList<>();
        for (DrugItem item : items) {
            Optional<DrugSummary> drug = lookupDrugOrEmpty(item.kdCode());
            Long drugId = drug.map(DrugSummary::drugId).orElse(null);
            prescription.addDrug(toPrescribedDrug(drugId, item));
            registered.add(toRegisteredItem(item, drug));
        }
        return registered;
    }

    private List<PrescribedDrugCandidate> buildCandidates(List<DrugItem> items,
                                                            List<RegisteredDrugItem> registered) {
        List<PrescribedDrugCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            DrugItem item = items.get(i);
            CandidateDecisionType decisionType = toCandidateDecisionType(item.decision());
            if (decisionType == null) continue;
            String optionsJson = resolveOptionsJson(item.candidateOptionsJson());
            if (optionsJson == null) continue;
            candidates.add(PrescribedDrugCandidate.create(i, decisionType, item.decision(), optionsJson));
        }
        return candidates;
    }

    private CandidateDecisionType toCandidateDecisionType(String decision) {
        if ("CONFIRM".equals(decision)) return CandidateDecisionType.CONFIRM;
        if ("MANUAL".equals(decision)) return CandidateDecisionType.MANUAL;
        return null;
    }

    private String resolveOptionsJson(String rawOptionsJson) {
        if (rawOptionsJson == null || rawOptionsJson.isBlank()) return null;
        try {
            List<Map<String, Object>> options = objectMapper.readValue(
                    rawOptionsJson, new TypeReference<>() {});
            List<Map<String, Object>> resolved = options.stream()
                    .map(this::resolveOptionEntry)
                    .filter(opt -> opt.containsKey("drugId"))
                    .toList();
            return resolved.isEmpty() ? null : objectMapper.writeValueAsString(resolved);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> resolveOptionEntry(Map<String, Object> option) {
        Object itemSeq = option.get("item_seq");
        if (itemSeq == null) return option;
        Optional<DrugSummary> drug = drugLookupPort.findByKdCode(itemSeq.toString());
        if (drug.isEmpty()) return Map.of();
        return Map.of("drugId", drug.get().drugId());
    }

    private Optional<DrugSummary> lookupDrugOrEmpty(String kdCode) {
        if (kdCode == null || kdCode.isBlank()) {
            return Optional.empty();
        }
        return drugLookupPort.findByKdCode(kdCode);
    }

    private RegisteredDrugItem toRegisteredItem(DrugItem item, Optional<DrugSummary> drug) {
        return new RegisteredDrugItem(
                drug.map(DrugSummary::drugId).orElse(null),
                drug.map(DrugSummary::kdCode).orElse(null),
                item.nameRaw(),
                drug.map(DrugSummary::name).orElse(null),
                item.confidence(),
                drug.map(DrugSummary::imageUrl).orElse(null),
                item.decision() != null ? item.decision() : "AUTO",
                null);
    }

    private PrescribedDrug toPrescribedDrug(Long drugId, DrugItem item) {
        return PrescribedDrug.builder()
                .drugId(drugId)
                .nameRaw(item.nameRaw())
                .doseAmount(item.doseAmount())
                .doseUnit(item.doseUnit())
                .frequency(item.frequency())
                .durationDays(item.durationDays())
                .confidence(item.confidence())
                .build();
    }

    private List<String> extractMatchedKdCodes(List<RegisteredDrugItem> registered) {
        return registered.stream()
                .filter(it -> it.kdCode() != null)
                .map(RegisteredDrugItem::kdCode)
                .toList();
    }

    private boolean hasCriticalWarning(List<InteractionWarning> warnings) {
        return warnings.stream().anyMatch(w -> w.severity() == InteractionSeverity.CRITICAL);
    }

    private List<String> extractCriticalMessages(List<InteractionWarning> warnings) {
        return warnings.stream()
                .filter(w -> w.severity() == InteractionSeverity.CRITICAL)
                .map(InteractionWarning::description)
                .toList();
    }

    private long countUnmatched(List<RegisteredDrugItem> items) {
        return items.stream().filter(it -> it.drugId() == null).count();
    }
}
