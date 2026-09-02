export const MIN_SCALE = 1;
export const MAX_SCALE = 4;
export const DOUBLE_TAP_SCALE = 2;

export function clamp(value: number, min: number, max: number): number {
  'worklet';
  return Math.min(max, Math.max(min, value));
}

export function nextDoubleTapScale(currentScale: number): number {
  'worklet';
  return currentScale > MIN_SCALE ? MIN_SCALE : DOUBLE_TAP_SCALE;
}

export function maxTranslate(scale: number, dimension: number): number {
  'worklet';
  return (Math.max(MIN_SCALE, scale) - MIN_SCALE) * dimension / 2;
}
