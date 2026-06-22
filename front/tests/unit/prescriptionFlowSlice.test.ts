import prescriptionFlowReducer, {
  addFromOcr,
  addFromExtract,
  addFromSearch,
  addManual,
  updateDoseAmount,
  removeItem,
  addSlot,
  removeSlot,
  setSlotTime,
  setStartDate,
  setEndDate,
  reset,
} from '../../src/store/slices/prescriptionFlowSlice';
import type { OcrItem, InteractionWarning, OcrExtractItem } from '../../src/types/prescription';

const DEFAULT_SLOTS = [
  { uid: 'morning', timeOfDay: 'MORNING' as const, customTime: '08:00:00' },
  { uid: 'noon',    timeOfDay: 'NOON' as const,    customTime: '12:30:00' },
  { uid: 'evening', timeOfDay: 'EVENING' as const, customTime: '19:00:00' },
];

const initialState = {
  items: [],
  prescriptionSlots: DEFAULT_SLOTS,
  prescribedAt: '',
  startDate: '',
  endDate: '',
  imageKey: null,
  memo: '',
  prescriptionId: null,
  ocrStatus: null,
  warnings: [],
};

const mockWarning: InteractionWarning = {
  drugCodeA: 'KD001',
  drugCodeB: 'KD002',
  nameA: '와파린정 2mg',
  nameB: '아스피린정 100mg',
  severity: 'CRITICAL',
  description: '와파린의 항응고 효과가 증가하여 출혈 위험이 높아집니다.',
  source: '식품의약품안전처',
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

const mockExtractItem: OcrExtractItem = {
  kdCode: 'KD001',
  nameRaw: '암로디핀정 5mg',
  doseAmount: 1,
  doseUnit: '정',
  frequency: 1,
  durationDays: 7,
  confidence: 0.9,
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
        warnings: [],
      }));
      expect(state.prescriptionId).toBe(42);
      expect(state.ocrStatus).toBe('DONE');
      expect(state.items).toHaveLength(1);
      expect(state.items[0].matchedName).toBe('암로디핀정 5mg');
      expect(state.items[0].source).toBe('OCR_AUTO');
    });

    it('warnings 필드 — 빈 배열 저장', () => {
      const state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem], warnings: [],
      }));
      expect(state.warnings).toEqual([]);
    });

    it('warnings 필드 — CRITICAL 병용금기 1건 저장', () => {
      const state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem], warnings: [mockWarning],
      }));
      expect(state.warnings).toHaveLength(1);
      expect(state.warnings[0].severity).toBe('CRITICAL');
      expect(state.warnings[0].nameA).toBe('와파린정 2mg');
      expect(state.warnings[0].source).toBe('식품의약품안전처');
    });

    it('warnings 필드 — response 에 warnings 누락 시 빈 배열 fallback', () => {
      const state = prescriptionFlowReducer(initialState, addFromOcr({
        prescriptionId: 1, ocrStatus: 'DONE', items: [mockOcrItem],
      } as any));
      expect(state.warnings).toEqual([]);
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

  describe('addFromExtract', () => {
    it('sets items, prescribedAt, startDate, imageKey from extract response', () => {
      const state = prescriptionFlowReducer(initialState, addFromExtract({
        items: [mockExtractItem],
        prescribedAt: '2026-06-22',
        imageKey: 'test-key',
      }));
      expect(state.prescribedAt).toBe('2026-06-22');
      expect(state.startDate).toBe('2026-06-22');
      expect(state.imageKey).toBe('test-key');
      expect(state.items).toHaveLength(1);
      expect(state.items[0].source).toBe('OCR_AUTO');
      expect(state.items[0].nameRaw).toBe('암로디핀정 5mg');
    });

    it('calculates endDate as startDate + max(durationDays) - 1', () => {
      const state = prescriptionFlowReducer(initialState, addFromExtract({
        items: [
          { ...mockExtractItem, durationDays: 7 },
          { ...mockExtractItem, kdCode: null, nameRaw: '모름약', durationDays: 14 },
        ],
        prescribedAt: '2026-06-22',
        imageKey: 'k',
      }));
      expect(state.endDate).toBe('2026-07-05');
    });

    it('leaves endDate empty when all durationDays are null/0', () => {
      const state = prescriptionFlowReducer(initialState, addFromExtract({
        items: [{ ...mockExtractItem, durationDays: null }],
        prescribedAt: '2026-06-22',
        imageKey: 'k',
      }));
      expect(state.endDate).toBe('');
    });

    it('marks unmatched item as MANUAL decision', () => {
      const state = prescriptionFlowReducer(initialState, addFromExtract({
        items: [{ ...mockExtractItem, kdCode: null }],
        prescribedAt: '2026-06-22',
        imageKey: 'k',
      }));
      expect(state.items[0].decision).toBe('MANUAL');
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
      }));
      expect(state.items).toHaveLength(1);
      expect(state.items[0].source).toBe('MANUAL_INPUT');
      expect(state.items[0].kdCode).toBeNull();
      expect(state.items[0].doseAmount).toBe(2);
    });
  });

  describe('prescriptionSlots — 처방전 단위 슬롯', () => {
    it('initial state has 3 default slots (아침/점심/저녁)', () => {
      const state = prescriptionFlowReducer(undefined, { type: '@@INIT' });
      expect(state.prescriptionSlots).toHaveLength(3);
      expect(state.prescriptionSlots[0].timeOfDay).toBe('MORNING');
      expect(state.prescriptionSlots[1].timeOfDay).toBe('NOON');
      expect(state.prescriptionSlots[2].timeOfDay).toBe('EVENING');
    });

    it('addSlot appends a new slot', () => {
      const state = prescriptionFlowReducer(initialState, addSlot({
        timeOfDay: 'MORNING', customTime: '07:30:00',
      }));
      expect(state.prescriptionSlots).toHaveLength(4);
      const added = state.prescriptionSlots.at(-1);
      expect(added?.timeOfDay).toBe('MORNING');
      expect(added?.customTime).toBe('07:30:00');
      expect(added?.uid).toBeTruthy();
    });

    it('removeSlot removes by uid', () => {
      const state = prescriptionFlowReducer(initialState, removeSlot('morning'));
      expect(state.prescriptionSlots).toHaveLength(2);
      expect(state.prescriptionSlots.find(s => s.uid === 'morning')).toBeUndefined();
    });

    it('setSlotTime updates customTime for matching uid', () => {
      const state = prescriptionFlowReducer(initialState, setSlotTime({
        uid: 'morning', customTime: '07:00:00',
      }));
      const morningSlot = state.prescriptionSlots.find(s => s.uid === 'morning');
      expect(morningSlot?.customTime).toBe('07:00:00');
    });

    it('setSlotTime does nothing for unknown uid', () => {
      const state = prescriptionFlowReducer(initialState, setSlotTime({
        uid: 'nonexistent', customTime: '07:00:00',
      }));
      expect(state.prescriptionSlots).toHaveLength(3);
    });
  });

  describe('날짜 관리', () => {
    it('setStartDate updates startDate', () => {
      const state = prescriptionFlowReducer(initialState, setStartDate('2026-07-01'));
      expect(state.startDate).toBe('2026-07-01');
    });

    it('setEndDate updates endDate', () => {
      const state = prescriptionFlowReducer(initialState, setEndDate('2026-07-30'));
      expect(state.endDate).toBe('2026-07-30');
    });

    it('setEndDate with empty string sets 무기한', () => {
      let state = prescriptionFlowReducer(initialState, setEndDate('2026-07-30'));
      state = prescriptionFlowReducer(state, setEndDate(''));
      expect(state.endDate).toBe('');
    });
  });

  describe('updateDoseAmount', () => {
    it('updates dose amount', () => {
      let state = prescriptionFlowReducer(initialState, addManual({
        nameRaw: '테스트', doseAmount: 1, doseUnit: '정', frequency: 1, durationDays: 7,
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

  describe('careGroupId / patientId 의존성 없음', () => {
    it('초기 state에 careGroupId / patientId 필드 없음', () => {
      const state = prescriptionFlowReducer(undefined, { type: '@@INIT' });
      expect(state).not.toHaveProperty('careGroupId');
      expect(state).not.toHaveProperty('patientId');
    });
  });
});
