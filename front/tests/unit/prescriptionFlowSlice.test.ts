import prescriptionFlowReducer, {
  addFromOcr,
  addFromSearch,
  addManual,
  updateSlots,
  updateDoseAmount,
  removeItem,
  setPatientId,
  reset,
} from '../../src/store/slices/prescriptionFlowSlice';
import type { OcrItem } from '../../src/types/prescription';

const initialState = {
  items: [],
  prescriptionId: null,
  patientId: null,
  ocrStatus: null,
  memo: '',
  imageKey: null,
};

const mockOcrItem: OcrItem = {
  drugId: 1,
  kdCode: 'KD001',
  matchedName: '암로디핀정 5mg',
  nameRaw: '암로디핀정 5mg',
  confidence: 0.98,
  imageUrl: null,
};

const mockOcrItemUnmatched: OcrItem = {
  drugId: null,
  kdCode: null,
  matchedName: null,
  nameRaw: '미확인약품 100mg',
  confidence: 0.45,
  imageUrl: null,
};

describe('prescriptionFlowSlice', () => {
  it('returns initial state', () => {
    expect(prescriptionFlowReducer(undefined, { type: '@@INIT' })).toEqual(initialState);
  });

  describe('addFromOcr', () => {
    it('sets prescriptionId and items from OCR response', () => {
      const state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 42,
        ocrStatus: 'DONE',
        items: [mockOcrItem],
      }));
      expect(state.prescriptionId).toBe(42);
      expect(state.ocrStatus).toBe('DONE');
      expect(state.items).toHaveLength(1);
      expect(state.items[0].matchedName).toBe('암로디핀정 5mg');
      expect(state.items[0].source).toBe('OCR_AUTO');
    });

    it('marks unmatched item as MANUAL decision', () => {
      const state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1,
        ocrStatus: 'MANUAL',
        items: [mockOcrItemUnmatched],
      }));
      expect(state.items[0].decision).toBe('MANUAL');
      expect(state.items[0].kdCode).toBeNull();
    });
  });

  describe('addFromSearch', () => {
    it('adds a MANUAL_SEARCH item with confidence 1.0', () => {
      const state = prescriptionFlowReducer(initialState, addFromSearch({
        kdCode: 'KD999',
        nameRaw: '테스트약',
        matchedName: '테스트약정',
        imageUrl: null,
      }));
      expect(state.items).toHaveLength(1);
      expect(state.items[0].source).toBe('MANUAL_SEARCH');
      expect(state.items[0].confidence).toBe(1.0);
    });
  });

  describe('addManual', () => {
    it('adds a MANUAL_INPUT item with null kdCode', () => {
      const state = prescriptionFlowReducer(initialState, addManual({
        nameRaw: '영양제',
        doseAmount: 2,
        doseUnit: '정',
        frequency: 1,
        durationDays: 30,
        slots: { morning: true, noon: false, evening: false, bedtime: false },
      }));
      expect(state.items).toHaveLength(1);
      expect(state.items[0].source).toBe('MANUAL_INPUT');
      expect(state.items[0].kdCode).toBeNull();
      expect(state.items[0].doseAmount).toBe(2);
    });
  });

  describe('updateSlots', () => {
    it('updates slots for the matching item', () => {
      let state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem],
      }));
      const id = state.items[0].id;
      state = prescriptionFlowReducer(state, updateSlots({
        id,
        slots: { morning: false, noon: true, evening: true, bedtime: false },
      }));
      expect(state.items[0].slots.noon).toBe(true);
      expect(state.items[0].slots.morning).toBe(false);
    });
  });

  describe('updateDoseAmount', () => {
    it('updates dose amount', () => {
      let state = prescriptionFlowReducer(initialState, addManual({
        nameRaw: '테스트', doseAmount: 1, doseUnit: '정', frequency: 1, durationDays: 7,
        slots: { morning: true, noon: false, evening: false, bedtime: false },
      }));
      const id = state.items[0].id;
      state = prescriptionFlowReducer(state, updateDoseAmount({ id, amount: 3 }));
      expect(state.items[0].doseAmount).toBe(3);
    });
  });

  describe('removeItem', () => {
    it('removes item by id', () => {
      let state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem],
      }));
      const id = state.items[0].id;
      state = prescriptionFlowReducer(state, removeItem(id));
      expect(state.items).toHaveLength(0);
    });
  });

  describe('reset', () => {
    it('resets to initial state', () => {
      let state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem],
      }));
      state = prescriptionFlowReducer(state, reset());
      expect(state).toEqual(initialState);
    });
  });

  describe('setPatientId', () => {
    it('sets patientId and retains other state', () => {
      const state = prescriptionFlowReducer(initialState, setPatientId(42));
      expect(state.patientId).toBe(42);
      expect(state.items).toHaveLength(0);
    });

    it('patientId is null in initial state (careGroupId 의존성 없음)', () => {
      expect(initialState.patientId).toBeNull();
    });
  });
});
