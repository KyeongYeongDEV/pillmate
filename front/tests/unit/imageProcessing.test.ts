import { downsizeForOcr, OCR_IMAGE_MAX_WIDTH, OCR_IMAGE_COMPRESS } from '../../src/lib/imageProcessing';
import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';

jest.mock('expo-image-manipulator', () => ({
  SaveFormat: { JPEG: 'jpeg', PNG: 'png' },
  manipulateAsync: jest.fn(),
}));

const mockManipulate = manipulateAsync as jest.MockedFunction<typeof manipulateAsync>;

describe('downsizeForOcr', () => {
  beforeEach(() => mockManipulate.mockReset());

  it('1024px resize + JPEG compress 0.8 옵션으로 호출', async () => {
    mockManipulate.mockResolvedValue({ uri: 'file://small.jpg', width: 1024, height: 1365 });
    await downsizeForOcr('file://original-4mb.jpg');
    expect(mockManipulate).toHaveBeenCalledWith(
      'file://original-4mb.jpg',
      [{ resize: { width: OCR_IMAGE_MAX_WIDTH } }],
      { compress: OCR_IMAGE_COMPRESS, format: SaveFormat.JPEG },
    );
  });

  it('다운사이즈된 결과(width<=1024) 반환', async () => {
    mockManipulate.mockResolvedValue({ uri: 'file://small.jpg', width: 1024, height: 1365 });
    const result = await downsizeForOcr('file://huge.jpg');
    expect(result.uri).toBe('file://small.jpg');
    expect(result.width).toBeLessThanOrEqual(OCR_IMAGE_MAX_WIDTH);
  });

  it('상수: 최대 너비 1024, 압축률 0.8', () => {
    expect(OCR_IMAGE_MAX_WIDTH).toBe(1024);
    expect(OCR_IMAGE_COMPRESS).toBe(0.8);
  });
});
