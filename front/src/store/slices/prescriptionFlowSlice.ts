import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type {
  DrugListItem, DrugSource, InteractionWarning, OcrItem, OcrStatus,
  OcrExtractItem, PrescriptionSlotDraft, PrescriptionTimeOfDay,
} from '@/types/prescription';

function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

function parseConfidence(raw: number | string | null): number | null {
  if (raw == null) return null;
  const n = typeof raw === 'string' ? parseFloat(raw) : raw;
  return isNaN(n) ? null : n;
}

function ocrItemToListItem(item: OcrItem): DrugListItem {
  return {
    id: `${item.kdCode ?? item.nameRaw}-${Date.now()}-${Math.random()}`,
    source: 'OCR_AUTO' as DrugSource,
    kdCode: item.kdCode,
    nameRaw: item.nameRaw,
    matchedName: item.matchedName,
    imageUrl: item.imageUrl,
    confidence: parseConfidence(item.confidence),
    doseAmount: 1,
    doseUnit: '정',
    frequency: 1,
    durationDays: 7,
    decision: item.drugId == null ? 'MANUAL' : 'AUTO',
  };
}

function extractItemToListItem(item: OcrExtractItem): DrugListItem {
  return {
    id: `extract-${item.nameRaw}-${Date.now()}-${Math.random()}`,
    source: 'OCR_AUTO' as DrugSource,
    kdCode: item.kdCode,
    nameRaw: item.nameRaw,
    matchedName: null,
    imageUrl: null,
    confidence: item.confidence,
    doseAmount: item.doseAmount ?? 1,
    doseUnit: item.doseUnit ?? '정',
    frequency: item.frequency ?? 1,
    durationDays: item.durationDays ?? 7,
    decision: item.kdCode == null ? 'MANUAL' : 'AUTO',
  };
}

// 슬롯별 표준 알림 시간 (HH:mm) — BEDTIME 포함 (FE 4슬롯 대비)
export const DEFAULT_CUSTOM_TIME: Record<string, string> = {
  MORNING: '08:00',
  NOON:    '12:30',
  EVENING: '19:00',
  BEDTIME: '22:00',
};

const DEFAULT_PRESCRIPTION_SLOTS: PrescriptionSlotDraft[] = [
  { uid: 'morning', timeOfDay: 'MORNING', customTime: `${DEFAULT_CUSTOM_TIME.MORNING}:00` },
  { uid: 'noon',    timeOfDay: 'NOON',    customTime: `${DEFAULT_CUSTOM_TIME.NOON}:00` },
  { uid: 'evening', timeOfDay: 'EVENING', customTime: `${DEFAULT_CUSTOM_TIME.EVENING}:00` },
];

interface PrescriptionFlowState {
  items: DrugListItem[];
  prescriptionSlots: PrescriptionSlotDraft[];
  prescribedAt: string;
  startDate: string;
  endDate: string;
  imageKey: string | null;
  memo: string;
  symptom: string;
  prescriptionId: number | null;
  ocrStatus: OcrStatus | null;
  warnings: InteractionWarning[];
}

const initialState: PrescriptionFlowState = {
  items: [],
  prescriptionSlots: DEFAULT_PRESCRIPTION_SLOTS,
  prescribedAt: '',
  startDate: '',
  endDate: '',
  imageKey: null,
  memo: '',
  symptom: '',
  prescriptionId: null,
  ocrStatus: null,
  warnings: [],
};

const prescriptionFlowSlice = createSlice({
  name: 'prescriptionFlow',
  initialState,
  reducers: {
    setImageKey(state, action: PayloadAction<string>) {
      state.imageKey = action.payload;
    },
    setOcrStatus(state, action: PayloadAction<OcrStatus>) {
      state.ocrStatus = action.payload;
    },
    setPrescriptionId(state, action: PayloadAction<number>) {
      state.prescriptionId = action.payload;
    },
    setPrescribedAt(state, action: PayloadAction<string>) {
      state.prescribedAt = action.payload;
      state.startDate = action.payload;
    },
    setStartDate(state, action: PayloadAction<string>) {
      state.startDate = action.payload;
    },
    setEndDate(state, action: PayloadAction<string>) {
      state.endDate = action.payload;
    },
    addFromOcr(state, action: PayloadAction<{ prescriptionId: number; ocrStatus: OcrStatus; items: OcrItem[]; warnings?: InteractionWarning[] }>) {
      state.prescriptionId = action.payload.prescriptionId;
      state.ocrStatus = action.payload.ocrStatus;
      state.items = action.payload.items.map(ocrItemToListItem);
      state.warnings = action.payload.warnings ?? [];
    },
    addFromExtract(state, action: PayloadAction<{ items: OcrExtractItem[]; prescribedAt: string; imageKey: string }>) {
      const { items, prescribedAt, imageKey } = action.payload;
      state.imageKey = imageKey;
      state.prescribedAt = prescribedAt;
      state.startDate = prescribedAt;
      const durations = items.map(i => i.durationDays ?? 0).filter(d => d > 0);
      const maxDuration = durations.length > 0 ? Math.max(...durations) : 0;
      state.endDate = maxDuration > 0 ? addDays(prescribedAt, maxDuration - 1) : '';
      state.items = items.map(extractItemToListItem);
      state.warnings = [];
    },
    addFromSearch(state, action: PayloadAction<{ kdCode: string; nameRaw: string; matchedName: string; imageUrl: string | null }>) {
      const drug = action.payload;
      state.items.push({
        id: `search-${drug.kdCode}-${Date.now()}`,
        source: 'MANUAL_SEARCH',
        kdCode: drug.kdCode,
        nameRaw: drug.nameRaw,
        matchedName: drug.matchedName,
        imageUrl: drug.imageUrl,
        confidence: 1.0,
        doseAmount: 1,
        doseUnit: '정',
        frequency: 1,
        durationDays: 7,
        decision: 'AUTO',
      });
    },
    addManual(state, action: PayloadAction<{ nameRaw: string; doseAmount: number; doseUnit: string; frequency: number; durationDays: number }>) {
      const input = action.payload;
      state.items.push({
        id: `manual-${Date.now()}-${Math.random()}`,
        source: 'MANUAL_INPUT',
        kdCode: null,
        nameRaw: input.nameRaw,
        matchedName: null,
        imageUrl: null,
        confidence: null,
        doseAmount: input.doseAmount,
        doseUnit: input.doseUnit,
        frequency: input.frequency,
        durationDays: input.durationDays,
        decision: 'MANUAL',
      });
    },
    addSlot(state, action: PayloadAction<{ timeOfDay: PrescriptionTimeOfDay; customTime: string }>) {
      const uid = `${action.payload.timeOfDay.toLowerCase()}-${Date.now()}`;
      state.prescriptionSlots.push({ uid, ...action.payload });
    },
    removeSlot(state, action: PayloadAction<string>) {
      state.prescriptionSlots = state.prescriptionSlots.filter(s => s.uid !== action.payload);
    },
    setSlotTime(state, action: PayloadAction<{ uid: string; customTime: string }>) {
      const slot = state.prescriptionSlots.find(s => s.uid === action.payload.uid);
      if (slot) slot.customTime = action.payload.customTime;
    },
    updateSlotCustomTime(state, action: PayloadAction<{ timeOfDay: PrescriptionTimeOfDay; customTime: string }>) {
      for (const slot of state.prescriptionSlots) {
        if (slot.timeOfDay === action.payload.timeOfDay) slot.customTime = action.payload.customTime;
      }
    },
    updateDoseAmount(state, action: PayloadAction<{ id: string; amount: number }>) {
      const item = state.items.find(i => i.id === action.payload.id);
      if (item) item.doseAmount = action.payload.amount;
    },
    replaceItem(state, action: PayloadAction<{ id: string; kdCode: string; matchedName: string; imageUrl: string | null }>) {
      const item = state.items.find(i => i.id === action.payload.id);
      if (item) {
        item.kdCode = action.payload.kdCode;
        item.matchedName = action.payload.matchedName;
        item.imageUrl = action.payload.imageUrl;
        item.confidence = 1.0;
        item.decision = 'CONFIRM';
        item.source = 'MANUAL_SEARCH';
      }
    },
    removeItem(state, action: PayloadAction<string>) {
      state.items = state.items.filter(i => i.id !== action.payload);
    },
    setMemo(state, action: PayloadAction<string>) {
      state.memo = action.payload;
    },
    setSymptom(state, action: PayloadAction<string>) {
      state.symptom = action.payload;
    },
    reset: () => initialState,
  },
});

export const {
  setImageKey, setOcrStatus, setPrescriptionId, setPrescribedAt,
  setStartDate, setEndDate,
  addFromOcr, addFromExtract, addFromSearch, addManual,
  addSlot, removeSlot, setSlotTime, updateSlotCustomTime,
  updateDoseAmount, replaceItem, removeItem,
  setMemo, setSymptom, reset,
} = prescriptionFlowSlice.actions;

export default prescriptionFlowSlice.reducer;
