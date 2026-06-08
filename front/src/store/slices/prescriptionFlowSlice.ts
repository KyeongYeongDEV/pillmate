import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type {
  DrugListItem, DrugSlots, DrugSource, InteractionWarning, OcrItem, OcrStatus,
} from '@/types/prescription';

interface PrescriptionFlowState {
  items: DrugListItem[];
  prescriptionId: number | null;
  ocrStatus: OcrStatus | null;
  memo: string;
  imageKey: string | null;
  warnings: InteractionWarning[];
}

const initialState: PrescriptionFlowState = {
  items: [],
  prescriptionId: null,
  ocrStatus: null,
  memo: '',
  imageKey: null,
  warnings: [],
};

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
    slots: { morning: true, noon: false, evening: false, bedtime: false },
    decision: item.drugId == null ? 'MANUAL' : 'AUTO',
  };
}

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
    addFromOcr(state, action: PayloadAction<{ prescriptionId: number; ocrStatus: OcrStatus; items: OcrItem[]; warnings?: InteractionWarning[] }>) {
      state.prescriptionId = action.payload.prescriptionId;
      state.ocrStatus = action.payload.ocrStatus;
      state.items = action.payload.items.map(ocrItemToListItem);
      state.warnings = action.payload.warnings ?? [];
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
        slots: { morning: true, noon: false, evening: false, bedtime: false },
        decision: 'AUTO',
      });
    },
    addManual(state, action: PayloadAction<{ nameRaw: string; doseAmount: number; doseUnit: string; frequency: number; durationDays: number; slots: DrugSlots }>) {
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
        slots: input.slots,
        decision: 'MANUAL',
      });
    },
    updateSlots(state, action: PayloadAction<{ id: string; slots: DrugSlots }>) {
      const item = state.items.find(i => i.id === action.payload.id);
      if (item) item.slots = action.payload.slots;
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
    reset: () => initialState,
  },
});

export const {
  setImageKey, setOcrStatus, setPrescriptionId,
  addFromOcr, addFromSearch, addManual,
  updateSlots, updateDoseAmount, replaceItem, removeItem,
  setMemo, reset,
} = prescriptionFlowSlice.actions;

export default prescriptionFlowSlice.reducer;
