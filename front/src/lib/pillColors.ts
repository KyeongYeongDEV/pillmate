export const PILL_COLORS = ['#5B9EF5', '#FF6B7A', '#7ECB7A', '#FFB84D', '#B57BF5'] as const;

function djb2(str: string): number {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) + hash + str.charCodeAt(i)) >>> 0;
  }
  return hash;
}

export function getPillColors(key: string): { colorA: string; colorB: string } {
  const len = PILL_COLORS.length;
  const idx = djb2(key) % len;
  return {
    colorA: PILL_COLORS[idx],
    colorB: PILL_COLORS[(idx + 2) % len],
  };
}
