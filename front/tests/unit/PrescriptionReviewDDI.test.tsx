import React from 'react';
import { Text } from 'react-native';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import { BackHandler } from 'react-native';
import PrescriptionReviewScreen from '@/app/prescription/review';
import { useRegisterPrescriptionMutation } from '@/store/slices/prescriptionApi';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { usePreventRemove } from '@react-navigation/core';
import { router } from 'expo-router';
import type { InteractionWarning } from '@/types/prescription';

// ─── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('expo-router', () => ({
  router: { replace: jest.fn(), back: jest.fn(), push: jest.fn() },
  useSegments: () => ['prescription', 'review'],
}));

jest.mock('expo-haptics', () => ({
  notificationAsync: jest.fn().mockResolvedValue(undefined),
  NotificationFeedbackType: { Success: 'SUCCESS' },
}));

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: any) => children,
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

// usePreventRemove: jest.fn() so we can assert on its args without NavigationContainer
jest.mock('@react-navigation/core', () => ({
  usePreventRemove: jest.fn(),
}));

jest.mock('@/store/slices/prescriptionApi', () => ({
  useRegisterPrescriptionMutation: jest.fn(),
}));

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetMyGroupsQuery: () => ({
    data: [{ groupId: 1, pinned: true, groupName: 'Family', role: 'GUARDIAN', memberCount: 2 }],
  }),
}));

jest.mock('@/store/hooks', () => ({
  useAppDispatch: jest.fn(),
  useAppSelector: jest.fn(),
}));

jest.mock('@/store/slices/prescriptionFlowSlice', () => ({
  addFromSearch: jest.fn(), removeItem: jest.fn(), updateDoseAmount: jest.fn(),
  addSlot: jest.fn(), removeSlot: jest.fn(), setSlotTime: jest.fn(),
  setStartDate: jest.fn(), setEndDate: jest.fn(),
  reset: jest.fn(() => ({ type: 'prescriptionFlow/reset' })),
}));

jest.mock('@/components/schedule/TimePicker', () => () => null);
jest.mock('@/components/prescription/DrugSearchModal', () => () => null);
jest.mock('@/components/prescription/DrugCard', () => () => null);
jest.mock('@/lib/constants', () => ({ MFDS_SOURCE: '식품의약품안전처' }));

// DDIWarningCard: 팩토리에서 require 없이 jest.fn() 만 반환, mockImplementation 은 beforeEach 에서
jest.mock('@/components/prescription/DDIWarningCard', () => ({
  __esModule: true,
  default: jest.fn(),
}));
import DDIWarningCardMock from '@/components/prescription/DDIWarningCard';

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const FLOW_STATE = {
  items: [
    {
      id: 'item-1', kdCode: 'KD001', nameRaw: '와파린정', matchedName: '와파린정',
      doseAmount: 1, doseUnit: '정', frequency: 1, durationDays: 7, confidence: 1, imageUrl: null,
    },
  ],
  prescriptionSlots: [{ uid: 'slot-1', timeOfDay: 'MORNING' as const, customTime: '08:00:00' }],
  prescribedAt: '2026-06-01', startDate: '2026-06-01', endDate: '2026-06-07', imageKey: null,
};

const CRITICAL_WARNING: InteractionWarning = {
  drugCodeA: 'KD001', drugCodeB: 'KD002',
  nameA: '와파린정', nameB: '아스피린정',
  severity: 'CRITICAL', description: '출혈 위험이 높아집니다.', source: '식품의약품안전처',
};

// ─── Setup ────────────────────────────────────────────────────────────────────

function setupRegisterMock(warnings: InteractionWarning[]) {
  const unwrap = jest.fn().mockResolvedValue({
    prescriptionId: 42, ocrStatus: 'DONE' as const, items: [], warnings,
  });
  (useRegisterPrescriptionMutation as jest.Mock).mockReturnValue([
    jest.fn().mockReturnValue({ unwrap }), { isLoading: false },
  ]);
}

beforeEach(() => {
  jest.clearAllMocks();

  (useAppDispatch as jest.Mock).mockReturnValue(jest.fn());
  (useAppSelector as jest.Mock).mockImplementation(
    (selector: any) => selector({ prescriptionFlow: FLOW_STATE }),
  );

  // DDIWarningCard 렌더 시 warning.description 을 Text 로 출력 (팩토리 외부에서 React/Text 참조 OK)
  // unknown 경유 캐스트: MemoExoticComponent ↔ jest.Mock 직접 변환 불가 (TS2352)
  (DDIWarningCardMock as unknown as jest.Mock).mockImplementation(({ warning }: any) =>
    React.createElement(Text, { testID: `ddi-card-${warning.severity}` }, warning.description),
  );

  jest.spyOn(BackHandler, 'addEventListener').mockReturnValue({ remove: jest.fn() } as any);
});

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('PrescriptionReviewScreen — DDI 병용금기 우회 방지 (T-FE-DDI-WARN-P0FIX)', () => {
  it('① warnings=[CRITICAL] → 경고 렌더 + usePreventRemove(true) + ack 전 홈 이동 안 함', async () => {
    // given
    setupRegisterMock([CRITICAL_WARNING]);
    render(<PrescriptionReviewScreen />);

    // when
    await act(async () => {
      fireEvent.press(screen.getByLabelText('약봉투 등록'));
    });

    // then — DDIWarningCard 렌더 → 경고 설명 텍스트 존재
    expect(screen.getByText('출혈 위험이 높아집니다.')).toBeTruthy();
    // then — back 방지 훅 true 로 호출됨
    expect(usePreventRemove).toHaveBeenCalledWith(true, expect.any(Function));
    // then — 확인 전 홈 이동 없음 (의료 P0)
    expect(router.replace).not.toHaveBeenCalled();
  });

  it('② 확인했습니다 → 홈 이동', async () => {
    // given
    setupRegisterMock([CRITICAL_WARNING]);
    render(<PrescriptionReviewScreen />);

    await act(async () => { fireEvent.press(screen.getByLabelText('약봉투 등록')); });

    // when
    await act(async () => {
      fireEvent.press(screen.getByLabelText('병용금기 경고 확인'));
    });

    // then
    expect(router.replace).toHaveBeenCalledWith('/(tabs)/home');
  });

  it('③ warnings=[] → DDI 경고 없이 즉시 홈 이동', async () => {
    // given
    setupRegisterMock([]);
    render(<PrescriptionReviewScreen />);

    // when
    await act(async () => { fireEvent.press(screen.getByLabelText('약봉투 등록')); });

    // then — DDIWarningCard 미렌더 (ddiWarnings null → map 결과 없음)
    expect(screen.queryByText('출혈 위험이 높아집니다.')).toBeNull();
    // then — usePreventRemove 는 false 로만 호출 (warnings 없으므로)
    expect(usePreventRemove).not.toHaveBeenCalledWith(true, expect.any(Function));
    // then — 즉시 홈 이동
    expect(router.replace).toHaveBeenCalledWith('/(tabs)/home');
  });

  it('④ warnings 표시 중 hardwareBackPress 핸들러 → true 반환 (Android 뒤로가기 실효 차단)', async () => {
    // given — BackHandler 핸들러 캡처용 spy 재설정
    // 객체 프로퍼티 사용: TypeScript 가 let 변수를 null 로 narrowing 하지 못하도록
    const captured = { handler: null as ((() => boolean) | null) };
    jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event: any, handler: any) => {
      captured.handler = handler;
      return { remove: jest.fn() } as any;
    });

    setupRegisterMock([CRITICAL_WARNING]);
    render(<PrescriptionReviewScreen />);

    // when — warnings 발생 → useEffect BackHandler 등록
    await act(async () => { fireEvent.press(screen.getByLabelText('약봉투 등록')); });

    // then — 핸들러가 등록됐고 실제로 true 반환 (기본 뒤로가기 동작 차단)
    expect(captured.handler).not.toBeNull();
    const handlerFn = captured.handler as () => boolean;
    expect(handlerFn()).toBe(true);
  });

  it('⑤ warnings 표시 중 헤더 뒤로 버튼 disabled — 이탈 시도 차단 (시각 + 인터랙션)', async () => {
    // given
    setupRegisterMock([CRITICAL_WARNING]);
    render(<PrescriptionReviewScreen />);

    // 등록 전: accessibilityState.disabled 없거나 false
    expect(screen.getByLabelText('뒤로').props.accessibilityState?.disabled).toBeFalsy();

    // when — warnings 등장
    await act(async () => { fireEvent.press(screen.getByLabelText('약봉투 등록')); });

    // then — Pressable disabled=true → accessibilityState.disabled=true (RN 자동 전파)
    expect(screen.getByLabelText('뒤로').props.accessibilityState?.disabled).toBe(true);
  });
});
