import { apiFetch } from './client';
import type {
  Drug,
  DrugSearchResult,
  OcrInput,
  RegisterPrescriptionResponse,
  UploadUrlInput,
  UploadUrlResponse,
} from '@/types/prescription';

export const prescriptionApi = {
  issueUploadUrl: (input: UploadUrlInput) =>
    apiFetch<UploadUrlResponse>('/prescriptions/upload-url', { method: 'POST', body: input }),

  uploadToS3: async (uploadUrl: string, imageUri: string): Promise<void> => {
    const res = await fetch(imageUri);
    const blob = await res.blob();
    await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'image/jpeg',
        'x-amz-server-side-encryption': 'AES256',
      },
      body: blob,
    });
  },

  ocr: (input: OcrInput) =>
    apiFetch<RegisterPrescriptionResponse>('/prescriptions/ocr', { method: 'POST', body: input }),

  register: (input: object) =>
    apiFetch<RegisterPrescriptionResponse>('/prescriptions', { method: 'POST', body: input }),
};

export const drugApi = {
  search: (q: string) =>
    apiFetch<DrugSearchResult[]>(`/drugs/search?q=${encodeURIComponent(q)}`, { auth: false }),

  detail: (kdCode: string) =>
    apiFetch<Drug>(`/drugs/${kdCode}`, { auth: false }),
};
