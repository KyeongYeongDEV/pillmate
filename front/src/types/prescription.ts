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
  slots: DrugSlots;
  decision?: 'AUTO' | 'CONFIRM' | 'MANUAL';
  candidateId?: number;
}
