import { joinGroupErrorMessage } from '../../src/lib/caregroup/joinError';

describe('joinGroupErrorMessage — 그룹 참여 에러 매핑', () => {
  it('410 → 만료/잘못된 코드 문구', () => {
    expect(joinGroupErrorMessage({ status: 410, data: {} }))
      .toBe('만료되었거나 잘못된 코드예요');
  });

  it('404 → 코드 없음 문구', () => {
    expect(joinGroupErrorMessage({ status: 404, data: {} }))
      .toBe('초대 코드를 찾을 수 없어요');
  });

  it('TIMEOUT_ERROR → 타임아웃 문구', () => {
    expect(joinGroupErrorMessage({ status: 'TIMEOUT_ERROR', error: 'timeout' }))
      .toBe('연결 시간이 초과됐어요. 네트워크를 확인해주세요');
  });

  it('기타 status + envelope 메시지 → 서버 메시지 우선', () => {
    expect(joinGroupErrorMessage({ status: 409, data: { error: { message: '이미 참여한 그룹이에요' } } }))
      .toBe('이미 참여한 그룹이에요');
  });

  it('알 수 없는 에러 → 기본 문구', () => {
    expect(joinGroupErrorMessage(undefined)).toBe('초대 코드가 올바르지 않아요');
    expect(joinGroupErrorMessage(new Error('x'))).toBe('초대 코드가 올바르지 않아요');
  });
});
