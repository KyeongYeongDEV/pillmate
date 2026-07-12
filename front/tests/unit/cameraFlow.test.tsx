import React from 'react';
import { Alert } from 'react-native';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import CameraScreen from '@/app/prescription/camera';
import { prescriptionApi } from '@/lib/api/prescription';
import { router } from 'expo-router';

// ─── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('expo-router', () => ({
  router: { replace: jest.fn(), push: jest.fn(), back: jest.fn() },
}));

jest.mock('expo-camera', () => {
  const { forwardRef, useImperativeHandle } = require('react');
  return {
    CameraView: forwardRef((_props: unknown, ref: unknown) => {
      useImperativeHandle(ref, () => ({
        takePictureAsync: jest.fn().mockResolvedValue({ uri: 'file://captured.jpg' }),
      }));
      return null;
    }),
    useCameraPermissions: () => [{ granted: true }, jest.fn()],
  };
});

jest.mock('expo-image-picker', () => ({
  requestMediaLibraryPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  launchImageLibraryAsync: jest.fn().mockResolvedValue({
    canceled: false,
    assets: [{ uri: 'file://gallery.jpg' }],
  }),
}));

jest.mock('expo-haptics', () => ({
  notificationAsync: jest.fn().mockResolvedValue(undefined),
  impactAsync: jest.fn().mockResolvedValue(undefined),
  NotificationFeedbackType: { Success: 'SUCCESS' },
  ImpactFeedbackStyle: { Medium: 'MEDIUM' },
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('@/store/hooks', () => ({
  useAppDispatch: () => jest.fn(),
}));

jest.mock('@/store/slices/prescriptionFlowSlice', () => ({
  addFromExtract: jest.fn(() => ({ type: 'prescriptionFlow/addFromExtract' })),
  addFromOcr: jest.fn(() => ({ type: 'prescriptionFlow/addFromOcr' })),
  setImageKey: jest.fn(() => ({ type: 'prescriptionFlow/setImageKey' })),
}));

jest.mock('@/lib/api/prescription', () => ({
  prescriptionApi: {
    issueUploadUrl: jest.fn().mockResolvedValue({ uploadUrl: 'https://s3/put', objectKey: 'key-1' }),
    uploadToS3: jest.fn().mockResolvedValue(undefined),
    ocrExtract: jest.fn().mockResolvedValue({ items: [] }),
    ocr: jest.fn(),
  },
}));

jest.mock('@/hooks/useOcrInFlight', () => ({
  useOcrInFlight: () => ({
    begin: () => ({ allowed: true, elapsedMs: 0, attempts: 1 }),
    end: jest.fn(),
    hashImageUri: jest.fn().mockResolvedValue('hash-abc'),
  }),
}));

jest.mock('@/lib/imageProcessing', () => ({
  downsizeForOcr: jest.fn().mockResolvedValue({ uri: 'file://small.jpg', width: 1024, height: 1365 }),
}));

jest.mock('@/components/prescription/CameraGuideOverlay', () => () => null);

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('camera 등록 흐름 (B방식 통일)', () => {
  beforeEach(() => jest.clearAllMocks());

  it('갤러리 선택 → ocrExtract 호출(자동등록 ocr 미사용)', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    await waitFor(() => {
      expect(prescriptionApi.ocrExtract).toHaveBeenCalledTimes(1);
    });
    expect(prescriptionApi.ocr).not.toHaveBeenCalled();
  });

  it('인식 후 review.tsx 로 라우팅 (confirm 미경유)', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    await waitFor(() => {
      expect(router.replace).toHaveBeenCalledWith('/prescription/review');
    });
    expect(router.replace).not.toHaveBeenCalledWith('/prescription/confirm');
  });

  it('주민번호 감지(piiDetected) → review 라우팅 차단 + 경고 Alert', async () => {
    (prescriptionApi.ocrExtract as jest.Mock).mockResolvedValueOnce({ items: [], piiDetected: true });
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    await waitFor(() =>
      expect(alertSpy).toHaveBeenCalledWith('주민번호가 보여요', '가리고 다시 촬영해주세요.'),
    );
    expect(router.replace).not.toHaveBeenCalledWith('/prescription/review');

    alertSpy.mockRestore();
  });

  it('실패 후 다시 시도 → 업로드 재호출 0, ocrExtract만 재호출', async () => {
    (prescriptionApi.ocrExtract as jest.Mock)
      .mockRejectedValueOnce(new Error('quota 429'))
      .mockResolvedValueOnce({ items: [] });

    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    // 첫 시도 실패 → '다시 시도' 버튼 노출
    await waitFor(() => expect(screen.getByLabelText('다시 시도')).toBeTruthy());
    expect(prescriptionApi.issueUploadUrl).toHaveBeenCalledTimes(1);
    expect(prescriptionApi.uploadToS3).toHaveBeenCalledTimes(1);
    expect(prescriptionApi.ocrExtract).toHaveBeenCalledTimes(1);

    // 다시 시도 → 같은 imageKey 로 extract만 재호출 (업로드 0)
    fireEvent.press(screen.getByLabelText('다시 시도'));
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/prescription/review'));
    expect(prescriptionApi.issueUploadUrl).toHaveBeenCalledTimes(1); // 증가 없음
    expect(prescriptionApi.uploadToS3).toHaveBeenCalledTimes(1); // 증가 없음
    expect(prescriptionApi.ocrExtract).toHaveBeenCalledTimes(2); // 재호출
  });

  it('촬영 → 미리보기만 뜨고 사용하기 전까지 서버 호출 0', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('촬영'));

    await waitFor(() => expect(screen.getByLabelText('사용하기')).toBeTruthy());
    expect(screen.getByLabelText('다시 찍기')).toBeTruthy();
    expect(prescriptionApi.issueUploadUrl).not.toHaveBeenCalled();
    expect(prescriptionApi.uploadToS3).not.toHaveBeenCalled();
    expect(prescriptionApi.ocrExtract).not.toHaveBeenCalled();
  });

  it('미리보기에서 다시 찍기 → 카메라로 복귀, 서버 호출 0', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('촬영'));
    await waitFor(() => expect(screen.getByLabelText('다시 찍기')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('다시 찍기'));

    await waitFor(() => expect(screen.getByLabelText('촬영')).toBeTruthy());
    expect(screen.queryByLabelText('사용하기')).toBeNull();
    expect(prescriptionApi.issueUploadUrl).not.toHaveBeenCalled();
    expect(prescriptionApi.uploadToS3).not.toHaveBeenCalled();
    expect(prescriptionApi.ocrExtract).not.toHaveBeenCalled();
  });

  it('미리보기에서 사용하기 → 기존 upload→ocrExtract→review 흐름 그대로', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('촬영'));
    await waitFor(() => expect(screen.getByLabelText('사용하기')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('사용하기'));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/prescription/review'));
    expect(prescriptionApi.issueUploadUrl).toHaveBeenCalledTimes(1);
    expect(prescriptionApi.uploadToS3).toHaveBeenCalledTimes(1);
    expect(prescriptionApi.ocrExtract).toHaveBeenCalledTimes(1);
  });
});
