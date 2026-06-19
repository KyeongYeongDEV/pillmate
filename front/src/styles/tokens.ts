// Design tokens — exact match with 디자인/styles/tokens.css

import { Dimensions, Platform, useWindowDimensions, type TextStyle } from 'react-native';

// RN 정석(orthodox) 반응형 전략.
// 폰트·치수는 폭-비례 scale 이 아니라 고정 dp. 물리 크기는 OS 픽셀 밀도에 위임한다.
// (구) moderateScale(width 비례 + 0.7 댐핑) 폐지 — 기기마다 레이아웃이 미세하게 흔들리고
// Android 가 iOS 보다 커 보이던 문제의 정석 해법(원인은 폰트패딩, 아래에서 제거).
//
// 폭-비례 scale 헬퍼는 외부 호출부(컴포넌트 다수) 점진 정리를 위해 시그니처만 유지하고
// 항등(고정 dp)으로 동작시킨다. 신규 코드는 고정 dp 리터럴 또는 flex 를 사용한다.
export const scale = (size: number): number => size;

// 타입스케일 단일 튜닝 레버. 1.0 = 디자인 원본 dp(노인 친화로 약간 큼).
// 합의된 컴팩트 크기보다 크게 느껴지면 이 값만 낮춘다(문서화된 1회 균일 축소).
const TYPE_SCALE = 1.0;

// 태블릿 분기 준비(레이아웃 변경은 후속, 유틸만 제공). width >= 600dp = 태블릿.
const TABLET_MIN_WIDTH = 600;
export const isTablet = (): boolean => Dimensions.get('window').width >= TABLET_MIN_WIDTH;
export const useIsTablet = (): boolean => useWindowDimensions().width >= TABLET_MIN_WIDTH;

// Android 는 동일 dp 여도 includeFontPadding 으로 글자 위/아래 여백이 추가돼 크게 보인다.
// 일괄 배율 대신 원인을 제거 — 공용 텍스트 토큰에만 적용(iOS 는 무시되는 키).
const androidTextMetricFix = Platform.OS === 'android' ? { includeFontPadding: false as const } : null;

type FontWeight = TextStyle['fontWeight'];
const textToken = (fontSize: number, lineHeight: number, fontWeight: FontWeight) => ({
  fontSize: Math.round(fontSize * TYPE_SCALE),
  lineHeight: Math.round(lineHeight * TYPE_SCALE),
  fontWeight,
  ...androidTextMetricFix,
});

export const colors = {
  // Primary blue
  primaryNormal: '#005eeb',    // --c-blue-45
  primaryStrong: '#0054d1',    // --c-blue-40
  primaryHeavy:  '#003e9c',    // --c-blue-30
  primaryBase:   '#0066ff',    // --c-blue-50 (C.accentBlue)

  // Green palette
  green30: '#006e25',          // --c-green-30
  green40: '#009632',          // --c-green-40
  green90: '#acfcc7',          // --c-green-90
  green95: '#d9ffe6',          // --c-green-95

  // Violet palette
  violet45: '#5b37ed',         // --c-violet-45
  violet95: '#f0ecfe',         // --c-violet-95

  // Orange palette
  orange40: '#d47800',         // --c-orange-40
  orange95: '#fef4e6',         // --c-orange-95

  // Blue extended
  blue95: '#eaf2fe',           // --c-blue-95
  blue99: '#f7fbff',           // --c-blue-99
  blue90: '#c9defe',           // --c-blue-90

  // Red palette (handoff Groups.html)
  red95: '#feecec',            // --c-red-95
  red40: '#e52222',            // --c-red-40
  red50: '#ff4242',            // --c-red-50

  // Yellow (note kind, handoff fallback — tokens.css 정의 X)
  yellow95: '#FEF4A8',
  yellow40: '#8a6f2a',         // note fg

  // Pink (member event)
  pink46: '#e846cd',           // --c-pink-46

  // Cyan
  cyan50: '#00bdde',           // --c-cyan-50

  // Static
  staticWhite: '#ffffff',

  // Member tint palette (avatar identity colors — patient/guardian/etc)
  patientOrange: '#FF7B2E',    // 환자 기본 tint
  guardianBlue:  '#0066FF',    // 보호자 기본 tint (=primaryBase, alias)
  accentTeal:    '#00BFA5',    // member tint 4
  accentPink:    '#E91E63',    // member tint 5
  fallbackGray:  '#888888',    // role unknown fallback

  // Semantic label
  labelNormal:      '#171719',                 // --c-coolNeutral-10
  labelStrong:      '#000000',
  labelNeutral:     'rgba(46, 47, 51, 0.88)', // --label-neutral
  labelAlternative: 'rgba(55, 56, 60, 0.61)', // --label-alternative
  labelAssistive:   'rgba(55, 56, 60, 0.28)', // --label-assistive
  labelDisable:     'rgba(55, 56, 60, 0.16)',

  // Backgrounds
  bgNormal:   '#ffffff',
  bgAlt:      '#f7f7f8',       // --c-coolNeutral-99
  bgElevated: '#ffffff',

  // Fill
  fillNormal: 'rgba(112, 115, 124, 0.08)',  // --fill-normal
  fillStrong: '#e1e2e4',                    // --c-coolNeutral-96 (--fill-strong)

  // Line / border
  line:          'rgba(112, 115, 124, 0.22)', // --line-normal
  lineSolidNorm: '#c2c4c8',                   // --c-coolNeutral-90

  // Status
  statusPositive:   '#00bf40',  // --c-green-50
  statusCautionary: '#ff9200',
  statusNegative:   '#ff4242',

  // Accent
  accentViolet: '#6541f2',

  // Inactive tab
  tabInactive: '#878a93',      // --c-coolNeutral-60
} as const;

// 고정 dp. full 은 원/캡슐 sentinel.
export const radius = {
  r4: 4, r6: 6, r8: 8, r10: 10, r12: 12,
  r14: 14, r16: 16, r20: 20, r24: 24, r32: 32, full: 9999,
} as const;

export const space = {
  s2: 2, s4: 4, s6: 6, s8: 8, s10: 10,
  s12: 12, s14: 14, s16: 16, s18: 18, s20: 20,
  s24: 24, s28: 28, s32: 32, s40: 40, s48: 48, s64: 64,
} as const;

// Font families — Pretendard JP 로드되면 사용, 없으면 system fallback (iOS: San Francisco / Android: Roboto)
// 핸드오프 명세: "Pretendard JP", "Pretendard Variable", system fallback
export const fontFamily = {
  sans: 'Pretendard',
  mono: 'Menlo',
} as const;

export const typography = {
  display1:  textToken(56, 72, '700'),
  display2:  textToken(40, 52, '700'),
  title1:    textToken(32, 44, '700'),
  title2:    textToken(28, 38, '700'),
  title3:    textToken(24, 32, '700'),
  heading1:  textToken(22, 30, '700'),
  heading2:  textToken(20, 28, '700'),
  headline1: textToken(18, 26, '600'),
  headline2: textToken(17, 24, '600'),
  body1n:    textToken(16, 24, '500'),
  body1r:    textToken(16, 26, '500'),
  body2n:    textToken(15, 22, '500'),
  body2r:    textToken(15, 24, '500'),
  label1n:   textToken(14, 20, '500'),
  label2:    textToken(13, 18, '500'),
  caption1:  textToken(12, 16, '500'),
} as const;

export const shadows = {
  xsmall: { shadowOffset:{width:0,height:1}, shadowOpacity:0.10, shadowRadius:2, elevation:1 },
  small:  { shadowOffset:{width:0,height:4}, shadowOpacity:0.06, shadowRadius:6, elevation:2 },
  medium: { shadowOffset:{width:0,height:10},shadowOpacity:0.07, shadowRadius:15,elevation:4 },
  large:  { shadowOffset:{width:0,height:16},shadowOpacity:0.08, shadowRadius:24,elevation:8 },

  // Specific domain shadows (from design)
  timeSlotDone: { shadowColor: '#26C76C', shadowOffset:{width:0,height:6}, shadowOpacity:0.10, shadowRadius:18, elevation:3 },
  timeSlotNow:  { shadowColor: '#0066ff', shadowOffset:{width:0,height:4}, shadowOpacity:0.06, shadowRadius:12, elevation:2 },
  fab:          { shadowColor: '#0066ff', shadowOffset:{width:0,height:8}, shadowOpacity:0.38, shadowRadius:20, elevation:12 },
} as const;
