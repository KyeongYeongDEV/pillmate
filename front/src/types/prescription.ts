export type OcrStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED' | 'MANUAL';

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
  company: string | null;
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
  patientId: number;
  contentType: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
}

export interface OcrInput {
  patientId: number;
  prescribedAt: string;
  imageKey: string;
}

export interface DrugInteraction {
  kdCode: string;
  name: string;
  category: string;
  description: string;
}

export interface DrugDetail {
  id: number;
  kdCode: string;
  name: string;
  englishName: string | null;
  category: string | null;
  ingredient: string | null;
  company: string | null;
  imageUrl: string | null;
  efficacy: string[];
  dosage: string[];
  warnings: string[];
  interactions: DrugInteraction[];
  sideEffects: { name: string; rate: number }[];
  source: string;
  updatedAt: string;
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
