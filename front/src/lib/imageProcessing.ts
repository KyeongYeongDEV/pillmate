import { manipulateAsync, SaveFormat, type ImageResult } from 'expo-image-manipulator';

// 처방전 텍스트 인식은 1024px로 충분 — Gemini 입력 토큰이 픽셀 수에 비례하므로
// OCR 호출 전 다운사이징해 토큰/처리시간을 절반 이하로 줄인다.
export const OCR_IMAGE_MAX_WIDTH = 1024;
export const OCR_IMAGE_COMPRESS = 0.8;

export async function downsizeForOcr(uri: string): Promise<ImageResult> {
  return manipulateAsync(
    uri,
    [{ resize: { width: OCR_IMAGE_MAX_WIDTH } }],
    { compress: OCR_IMAGE_COMPRESS, format: SaveFormat.JPEG },
  );
}
