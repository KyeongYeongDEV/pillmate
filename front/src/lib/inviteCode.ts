const INVITE_CODE_LEN = 6;
const QR_PREFIX = 'PILLMATE:JOIN:';

export function extractInviteCode(payload: string): string | null {
  if (!payload) return null;
  const trimmed = payload.trim();
  const raw = trimmed.startsWith(QR_PREFIX)
    ? trimmed.slice(QR_PREFIX.length)
    : trimmed;
  const code = raw.toUpperCase();
  if (code.length !== INVITE_CODE_LEN) return null;
  if (!/^[A-Z0-9]+$/.test(code)) return null;
  return code;
}
