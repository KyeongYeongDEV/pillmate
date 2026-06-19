// Design tokens — exact match with 디자인/styles/tokens.css

import { Dimensions, PixelRatio, Platform, type TextStyle } from 'react-native';

// 기기 적응형 스케일(moderateScale) — 디자인 기준기기 iPhone 390×844 에서 scale(x)=x 로 불변.
// width 비율에 0.7 댐핑을 적용해 큰/작은 기기에서 과도한 확대·축소를 막고,
// PixelRatio 로 물리 픽셀에 정렬한다. react-native-size-matters 무의존 인라인 구현.
const BASE_WIDTH = 390;
const SCALE_DAMPING = 0.7;
const SCREEN_WIDTH = Dimensions.get('window').width;
const widthRatio = SCREEN_WIDTH / BASE_WIDTH;

export const scale = (size: number): number =>
  PixelRatio.roundToNearestPixel(size * (1 + (widthRatio - 1) * SCALE_DAMPING));

// Android 는 동일 dp 여도 includeFontPadding 으로 글자 위/아래 여백이 추가돼 크게 보인다.
// 일괄 배율 대신 원인을 제거 — 공용 텍스트 토큰에만 적용(iOS 는 무시되는 키).
const androidTextMetricFix = Platform.OS === 'android' ? { includeFontPadding: false as const } : null;

type FontWeight = TextStyle['fontWeight'];
const textToken = (fontSize: number, lineHeight: number, fontWeight: FontWeight) => ({
  fontSize: scale(fontSize),
  lineHeight: scale(lineHeight),
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

// borderRadius 도 글자·박스와 함께 균일 축소(기준기기 iOS 는 scale(x)=x 불변). full 은 원/캡슐 sentinel 이라 미스케일.
export const radius = {
  r4: scale(4), r6: scale(6), r8: scale(8), r10: scale(10), r12: scale(12),
  r14: scale(14), r16: scale(16), r20: scale(20), r24: scale(24), r32: scale(32), full: 9999,
} as const;

export const space = {
  s2: scale(2), s4: scale(4), s6: scale(6), s8: scale(8), s10: scale(10),
  s12: scale(12), s14: scale(14), s16: scale(16), s18: scale(18), s20: scale(20),
  s24: scale(24), s28: scale(28), s32: scale(32), s40: scale(40), s48: scale(48), s64: scale(64),
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
