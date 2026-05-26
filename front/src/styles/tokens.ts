export const colors = {
  primaryNormal: '#005eeb',
  primaryStrong: '#0054d1',
  primaryHeavy:  '#003e9c',
  labelNormal:      '#171719',
  labelStrong:      '#000000',
  labelNeutral:     'rgba(46, 47, 51, 0.88)',
  labelAlternative: 'rgba(55, 56, 60, 0.61)',
  labelAssistive:   'rgba(55, 56, 60, 0.28)',
  labelDisable:     'rgba(55, 56, 60, 0.16)',
  bgNormal:   '#ffffff',
  bgAlt:      '#f7f7f8',
  bgElevated: '#ffffff',
  bgDim:      'rgba(0, 0, 0, 0.08)',
  bgStrong:   'rgba(0, 0, 0, 0.28)',
  statusPositive:   '#00bf40',
  statusCautionary: '#ff9200',
  statusNegative:   '#ff4242',
  accentBlue:      '#0066ff',
  accentLightBlue: '#00aeff',
  accentViolet:    '#6541f2',
  accentPink:      '#f553da',
  accentOrange:    '#ff9200',
  accentRedOrange: '#ff5e00',
  accentLime:      '#58cf04',
  accentGreen:     '#00bf40',
  line: '#e5e5ea',
} as const;

export const radius = {
  r4: 4, r6: 6, r8: 8, r10: 10, r12: 12, r14: 14, r16: 16, r20: 20, r24: 24, r32: 32, full: 9999,
} as const;

export const space = {
  s2: 2, s4: 4, s6: 6, s8: 8, s10: 10, s12: 12, s16: 16, s20: 20,
  s24: 24, s32: 32, s40: 40, s48: 48, s64: 64,
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
} as const;
