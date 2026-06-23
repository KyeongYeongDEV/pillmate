export type OcrStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED' | 'MANUAL';

export type InteractionSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface InteractionWarning {
  drugCodeA: string;
  drugCodeB: string;
  nameA: string;
  nameB: string;
  severity: InteractionSeverity;
  description: string;
  source: string;
}

export interface RegisterPrescriptionItem {
  kdCode: string | null;
  nameRaw: string;
  doseAmount: number | string;
  doseUnit: string;
  frequency: number;
  durationDays: number;
  confidence: number | string | null;
}

export interface RegisterPrescriptionResponse {
  prescriptionId: number;
  ocrStatus: OcrStatus;
  items: OcrItem[];
  warnings?: InteractionWarning[];
}

export interface OcrItem {
  drugId: number | null;
  kdCode: string | null;
  matchedName: string | null;
  nameRaw: string;
  confidence: number | string | null;
  imageUrl: string | null;
}

export interface PrescriptionSummary {
  id: number;
  prescribedAt: string;
  ocrStatus: OcrStatus;
  drugCount: number;
  drugNames: string;
  createdAt: string;
}

export interface PrescriptionDetailDrug {
  nameRaw: string;
  matchedDrugName: string | null;
  matchedKdCode: string | null;
  doseAmount: number | null;
  doseUnit: string | null;
  frequency: number | null;
  durationDays: number | null;
  confidence: number | null;
}

export interface PrescriptionDetailView {
  id: number;
  prescribedAt: string;
  ocrStatus: OcrStatus;
  imageUrl: string | null;
  drugs: PrescriptionDetailDrug[];
}

export interface Drug {
  id: number;
  kdCode: string;
  name: string;
  ingredient: string | null;
  efficacy: string | null;
  dosage: string | null;
  company: string | null;
  imageUrl: string | null;
}

export interface DrugSearchResult {
  id: number;
  kdCode: string;
  name: string;
  ingredient: string | null;
  efficacy: string | null;
  form: string | null;
  imageUrl: string | null;
}

export interface Candidate {
  candidateId: number;
  drugId: number;
  kdCode: string;
  name: string;
  company: string | null;
  imageUrl: string | null;
  similarity: number;
}

export interface UploadUrlInput {
  contentType: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
}

export interface OcrInput {
  prescribedAt: string;
  imageKey: string;
}

export interface DrugDetail {
  id: number;
  kdCode: string;
  name: string;
  ingredient: string | null;
  efficacy: string | null;
  dosage: string | null;
  sideEffect: string | null;
  form: string | null;
  company: string | null;
  source: string | null;
  imageUrl: string | null;
}

export interface DrugSlots {
  morning: boolean;
  noon: boolean;
  evening: boolean;
  bedtime: boolean;
}

export type DrugSource = 'OCR_AUTO' | 'MANUAL_SEARCH' | 'MANUAL_INPUT';

export interface DrugListItem {
  id: string;
  source: DrugSource;
  kdCode: string | null;
  nameRaw: string;
  matchedName: string | null;
  imageUrl: string | null;
  confidence: number | null;
  doseAmount: number;
  doseUnit: string;
  frequency: number;
  durationDays: number;
  decision?: 'AUTO' | 'CONFIRM' | 'MANUAL';
  candidateId?: number;
}

// ── Extract-only OCR (신규 흐름: 추출만, 미등록) ──────────────────────────
export interface OcrExtractItem {
  kdCode: string | null;
  nameRaw: string;
  doseAmount: number | null;
  doseUnit: string | null;
  frequency: number | null;
  durationDays: number | null;
  confidence: number | null;
}

export interface OcrExtractResponse {
  items: OcrExtractItem[];
}

// ── 처방전 단위 슬롯 (알림 시각, 약별 아님) ────────────────────────────────
export type PrescriptionTimeOfDay = 'MORNING' | 'NOON' | 'EVENING';

export interface PrescriptionSlotDraft {
  uid: string;
  timeOfDay: PrescriptionTimeOfDay;
  customTime: string;
}

// ── 등록 API 입력 ────────────────────────────────────────────────────────
export interface PrescriptionSlotInput {
  timeOfDay: PrescriptionTimeOfDay;
  customTime: string;
}

export interface PrescriptionScheduleInput {
  careGroupId: number;
  slots: PrescriptionSlotInput[];
  startDate: string;
  endDate: string | null;
}

export interface RegisterPrescriptionInput {
  prescribedAt: string;
  imageKey: string | null;
  items: RegisterPrescriptionItem[];
  schedule: PrescriptionScheduleInput;
}
