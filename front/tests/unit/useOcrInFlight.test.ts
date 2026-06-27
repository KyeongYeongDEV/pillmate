import {
  beginOcr,
  endOcr,
  isOcrInFlight,
  resetOcrRegistry,
  hashImageUri,
  OCR_IN_FLIGHT_TTL_MS,
} from '../../src/hooks/useOcrInFlight';
import * as Crypto from 'expo-crypto';

jest.mock('expo-crypto', () => ({
  CryptoDigestAlgorithm: { SHA256: 'SHA-256' },
  digestStringAsync: jest.fn(),
}));

describe('useOcrInFlight registry', () => {
  beforeEach(() => resetOcrRegistry());

  it('첫 요청은 소유자(allowed=true)', () => {
    const r = beginOcr('hashA', 1000);
    expect(r.allowed).toBe(true);
    expect(r.elapsedMs).toBe(0);
    expect(r.attempts).toBe(1);
  });

  it('진행 중 같은 hash 재요청은 차단(allowed=false) + 경과/시도수', () => {
    beginOcr('hashA', 1000);
    const dup = beginOcr('hashA', 4000);
    expect(dup.allowed).toBe(false);
    expect(dup.elapsedMs).toBe(3000);
    expect(dup.attempts).toBe(2);
  });

  it('다른 hash는 영향 없이 병렬 허용', () => {
    beginOcr('hashA', 1000);
    const other = beginOcr('hashB', 1000);
    expect(other.allowed).toBe(true);
  });

  it('endOcr 후 같은 hash 재요청 허용(cleanup)', () => {
    beginOcr('hashA', 1000);
    endOcr('hashA');
    expect(isOcrInFlight('hashA', 2000)).toBe(false);
    const again = beginOcr('hashA', 2000);
    expect(again.allowed).toBe(true);
  });

  it('TTL 초과 시 stale 항목은 새 소유자로 대체', () => {
    beginOcr('hashA', 1000);
    const afterTtl = beginOcr('hashA', 1000 + OCR_IN_FLIGHT_TTL_MS + 1);
    expect(afterTtl.allowed).toBe(true);
    expect(afterTtl.attempts).toBe(1);
  });

  it('isOcrInFlight: 진행 중 true, TTL 초과 false', () => {
    beginOcr('hashA', 1000);
    expect(isOcrInFlight('hashA', 2000)).toBe(true);
    expect(isOcrInFlight('hashA', 1000 + OCR_IN_FLIGHT_TTL_MS + 1)).toBe(false);
  });

  it('hashImageUri는 expo-crypto SHA-256 사용', async () => {
    (Crypto.digestStringAsync as jest.Mock).mockResolvedValue('deadbeef');
    const hash = await hashImageUri('file://x.jpg');
    expect(Crypto.digestStringAsync).toHaveBeenCalledWith('SHA-256', 'file://x.jpg');
    expect(hash).toBe('deadbeef');
  });
});
