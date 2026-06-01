// Design tokens — exact match with 디자인/styles/tokens.css

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

export const radius = {
  r4: 4, r6: 6, r8: 8, r10: 10, r12: 12, r14: 14, r16: 16, r20: 20, r24: 24, r32: 32, full: 9999,
} as const;

export const space = {
  s2: 2, s4: 4, s6: 6, s8: 8, s10: 10, s12: 12, s14: 14, s16: 16, s18: 18, s20: 20,
  s24: 24, s28: 28, s32: 32, s40: 40, s48: 48, s64: 64,
} as const;

// Font families — Pretendard JP 로드되면 사용, 없으면 system fallback (iOS: San Francisco / Android: Roboto)
// 핸드오프 명세: "Pretendard JP", "Pretendard Variable", system fallback
export const fontFamily = {
  sans: 'Pretendard',
  mono: 'Menlo',
} as const;

export const typography = {
  display1:  { fontSize: 56, lineHeight: 72, fontWeight: '700' as const },
  display2:  { fontSize: 40, lineHeight: 52, fontWeight: '700' as const },
  title1:    { fontSize: 32, lineHeight: 44, fontWeight: '700' as const },
  title2:    { fontSize: 28, lineHeight: 38, fontWeight: '700' as const },
  title3:    { fontSize: 24, lineHeight: 32, fontWeight: '700' as const },
  heading1:  { fontSize: 22, lineHeight: 30, fontWeight: '700' as const },
  heading2:  { fontSize: 20, lineHeight: 28, fontWeight: '700' as const },
  headline1: { fontSize: 18, lineHeight: 26, fontWeight: '600' as const },
  headline2: { fontSize: 17, lineHeight: 24, fontWeight: '600' as const },
  body1n:    { fontSize: 16, lineHeight: 24, fontWeight: '500' as const },
  body1r:    { fontSize: 16, lineHeight: 26, fontWeight: '500' as const },
  body2n:    { fontSize: 15, lineHeight: 22, fontWeight: '500' as const },
  body2r:    { fontSize: 15, lineHeight: 24, fontWeight: '500' as const },
  label1n:   { fontSize: 14, lineHeight: 20, fontWeight: '500' as const },
  label2:    { fontSize: 13, lineHeight: 18, fontWeight: '500' as const },
  caption1:  { fontSize: 12, lineHeight: 16, fontWeight: '500' as const },
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
