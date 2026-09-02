import {
  clamp,
  maxTranslate,
  nextDoubleTapScale,
  MIN_SCALE,
  MAX_SCALE,
  DOUBLE_TAP_SCALE,
} from '@/lib/prescription/imageZoom';

describe('imageZoom clamp', () => {
  it('clamps below min to min', () => {
    expect(clamp(0.3, MIN_SCALE, MAX_SCALE)).toBe(MIN_SCALE);
  });
  it('clamps above max to max', () => {
    expect(clamp(9, MIN_SCALE, MAX_SCALE)).toBe(MAX_SCALE);
  });
  it('keeps in-range value', () => {
    expect(clamp(2.5, MIN_SCALE, MAX_SCALE)).toBe(2.5);
  });
});

describe('nextDoubleTapScale', () => {
  it('zooms in from 1x', () => {
    expect(nextDoubleTapScale(1)).toBe(DOUBLE_TAP_SCALE);
  });
  it('resets to 1x when already zoomed', () => {
    expect(nextDoubleTapScale(2)).toBe(MIN_SCALE);
    expect(nextDoubleTapScale(3.5)).toBe(MIN_SCALE);
  });
});

describe('maxTranslate', () => {
  it('is zero when not zoomed', () => {
    expect(maxTranslate(1, 400)).toBe(0);
  });
  it('grows with scale', () => {
    expect(maxTranslate(2, 400)).toBe(200);
    expect(maxTranslate(3, 400)).toBe(400);
  });
});
