import { ocrStatusChip } from '@/lib/ocrStatusLabel';

describe('ocrStatusChip', () => {
  it('각 상태별 한국어 라벨 매핑', () => {
    expect(ocrStatusChip('DONE').label).toBe('완료');
    expect(ocrStatusChip('MANUAL').label).toBe('확인 필요');
    expect(ocrStatusChip('FAILED').label).toBe('실패');
    expect(ocrStatusChip('PROCESSING').label).toBe('인식 중');
    expect(ocrStatusChip('PENDING').label).toBe('대기 중');
  });

  it('각 상태별 색상 존재', () => {
    expect(ocrStatusChip('DONE').color).toBeTruthy();
    expect(ocrStatusChip('FAILED').color).toBeTruthy();
  });
});
