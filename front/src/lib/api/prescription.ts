import { apiFetch } from './client';
import type {
  Drug,
  DrugSearchResult,
  OcrInput,
  OcrExtractResponse,
  RegisterPrescriptionResponse,
  RegisterPrescriptionInput,
  UploadUrlInput,
  UploadUrlResponse,
} from '@/types/prescription';

export const prescriptionApi = {
  issueUploadUrl: (input: UploadUrlInput) =>
    apiFetch<UploadUrlResponse>('/prescriptions/upload-url', { method: 'POST', body: input }),

  uploadToS3: async (uploadUrl: string, imageUri: string): Promise<void> => {
    const res = await fetch(imageUri);
    const blob = await res.blob();
    const s3Resp = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'image/jpeg',
        'x-amz-server-side-encryption': 'AES256',
      },
      body: blob,
    });
    if (!s3Resp.ok) throw new Error(`S3 업로드 실패: ${s3Resp.status}`);
  },

  ocr: (input: OcrInput) =>
    apiFetch<RegisterPrescriptionResponse>('/prescriptions/ocr', { method: 'POST', body: input }),

  ocrExtract: (input: { imageKey: string; prescribedAt: string }) =>
    apiFetch<OcrExtractResponse>('/prescriptions/ocr/extract', { method: 'POST', body: input }),

  register: (input: object) =>
    apiFetch<RegisterPrescriptionResponse>('/prescriptions', { method: 'POST', body: input }),

  registerNew: (input: RegisterPrescriptionInput) =>
    apiFetch<{ prescriptionId: number }>('/prescriptions', { method: 'POST', body: input }),
};

export const drugApi = {
  search: (q: string) =>
    apiFetch<DrugSearchResult[]>(`/drugs/search?q=${encodeURIComponent(q)}`, { auth: false }),

  detail: (kdCode: string) =>
    apiFetch<Drug>(`/drugs/${kdCode}`, { auth: false }),
};
