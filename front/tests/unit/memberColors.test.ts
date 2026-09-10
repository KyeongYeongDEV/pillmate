import { assignMemberColors, MEMBER_COLOR_PALETTE, SELECTABLE_COLOR_PALETTE } from '@/utils/memberColors';

describe('assignMemberColors', () => {
  it('서로 다른 userId 3명에게 서로 다른 색을 배정한다', () => {
    const colors = assignMemberColors([{ userId: 1 }, { userId: 2 }, { userId: 3 }]);
    const assigned = [colors.get(1), colors.get(2), colors.get(3)];
    expect(new Set(assigned).size).toBe(3);
  });

  it('입력 배열 순서를 뒤섞어도 각 userId 는 항상 같은 색을 받는다 (뷰어 무관성)', () => {
    const inOrder = assignMemberColors([{ userId: 10 }, { userId: 20 }, { userId: 30 }]);
    const shuffled = assignMemberColors([{ userId: 30 }, { userId: 10 }, { userId: 20 }]);
    expect(shuffled.get(10)).toBe(inOrder.get(10));
    expect(shuffled.get(20)).toBe(inOrder.get(20));
    expect(shuffled.get(30)).toBe(inOrder.get(30));
  });

  it('팔레트 크기보다 멤버가 많아도 에러 없이 순환 배정한다', () => {
    const members = Array.from({ length: MEMBER_COLOR_PALETTE.length + 3 }, (_, i) => ({ userId: i + 1 }));
    const colors = assignMemberColors(members);
    expect(colors.size).toBe(members.length);
    // 첫 멤버와 팔레트 한 바퀴 뒤 멤버는 같은 색으로 순환된다.
    expect(colors.get(MEMBER_COLOR_PALETTE.length + 1)).toBe(colors.get(1));
  });

  it('멤버가 직접 고른 color 가 있으면 포지션 팔레트 대신 그 값을 쓴다', () => {
    const chosen = '#123456';
    const colors = assignMemberColors([
      { userId: 1, color: chosen },
      { userId: 2, color: null },
      { userId: 3 },
    ]);
    expect(colors.get(1)).toBe(chosen);
    // color 가 없는 멤버는 포지션 기반 폴백(정렬 순서 index 로).
    expect(colors.get(2)).toBe(MEMBER_COLOR_PALETTE[1]);
    expect(colors.get(3)).toBe(MEMBER_COLOR_PALETTE[2]);
  });

  it('SELECTABLE_COLOR_PALETTE 는 팔레트 앞 10개', () => {
    expect(SELECTABLE_COLOR_PALETTE).toHaveLength(10);
    expect(SELECTABLE_COLOR_PALETTE).toEqual(MEMBER_COLOR_PALETTE.slice(0, 10));
  });
});
