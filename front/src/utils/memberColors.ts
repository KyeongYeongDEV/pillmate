// 그룹 구성원 고유색 팔레트. 서로 뚜렷이 구분되는 12색으로,
// adherence 표시색(statusPositive/Cautionary/Negative·primaryBase)과 최대한 안 겹치게 골랐다.
export const MEMBER_COLOR_PALETTE = [
  '#7E57C2', // violet
  '#26A69A', // teal
  '#EC407A', // pink
  '#5C6BC0', // indigo
  '#8D6E63', // brown
  '#9CCC65', // light green
  '#29B6F6', // sky blue
  '#AB47BC', // magenta
  '#FF7043', // coral
  '#78909C', // blue grey
  '#C0CA33', // lime
  '#5D4037', // dark brown
] as const;

// 사용자가 직접 고를 수 있는 고정 색 선택지 — 팔레트 12색 전체.
export const SELECTABLE_COLOR_PALETTE = MEMBER_COLOR_PALETTE;

// 색 배정은 보는 사람(뷰어 표시 순서)과 무관하게 항상 같은 결과여야 한다.
// 그래서 파라미터로 받은 배열 순서를 신뢰하지 않고 함수 내부에서 userId 오름차순으로 재정렬한 뒤 배정한다.
// 사용자가 직접 고른 색(color)이 있으면 그 값을 그대로 쓰고, 없으면 포지션 기반 팔레트 순환으로 폴백한다.
// 팔레트 길이를 넘으면 모듈로 순환 (실사용 그룹 규모에서는 거의 발생하지 않는다).
export function assignMemberColors<T extends { userId: number; color?: string | null }>(members: T[]): Map<number, string> {
  const ordered = [...members].sort((a, b) => a.userId - b.userId);
  const colorByUserId = new Map<number, string>();
  ordered.forEach((member, index) => {
    colorByUserId.set(member.userId, member.color ?? MEMBER_COLOR_PALETTE[index % MEMBER_COLOR_PALETTE.length]);
  });
  return colorByUserId;
}
