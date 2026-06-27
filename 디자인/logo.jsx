// PillMate Logo System
// Mark concept: Two pill halves joined → people connected through medication care.
// Geometric, single-stroke. Works at 16px favicon up to splash hero.

// ─────────────────────────────────────────────────────────────
// Main mark: a capsule made of two halves linked by a central node.
// ─────────────────────────────────────────────────────────────
function LogoMark({ size = 160, stroke = '#fff', simple = false }) {
  // Viewbox: 200x200, 20-unit grid
  // - Capsule body: rounded rect rotated -45deg
  // - Center dot represents the bond / connection
  const s = size;
  return (
    <svg width={s} height={s} viewBox="0 0 200 200" fill="none">
      <g transform="rotate(-45 100 100)">
        {/* Outer capsule */}
        <rect x="30" y="65" width="140" height="70" rx="35"
          stroke={stroke} strokeWidth={simple ? 18 : 14} fill="none" />
        {/* Split line / connection */}
        {!simple && (
          <line x1="100" y1="65" x2="100" y2="135"
            stroke={stroke} strokeWidth={14} strokeLinecap="round" />
        )}
        {/* Two heads (people) at the capsule ends */}
        {!simple && (
          <>
            <circle cx="55" cy="100" r="9" fill={stroke} />
            <circle cx="145" cy="100" r="9" fill={stroke} />
          </>
        )}
      </g>
    </svg>
  );
}

// ─── Variant: pill + heart (care) ─────────────────────────────
function LogoMarkHeart({ size = 160, stroke = '#fff' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 200 200" fill="none">
      <g transform="rotate(-45 100 100)">
        <rect x="30" y="65" width="140" height="70" rx="35"
          stroke={stroke} strokeWidth={14} fill="none" />
        <line x1="100" y1="65" x2="100" y2="135"
          stroke={stroke} strokeWidth={14} strokeLinecap="round" />
      </g>
      {/* Heart at center */}
      <g transform="translate(100 100)">
        <path d="M 0 8 C -10 -2, -18 -10, -10 -18 C -4 -22, 0 -18, 0 -12 C 0 -18, 4 -22, 10 -18 C 18 -10, 10 -2, 0 8 Z"
          fill={stroke} stroke="none" />
      </g>
    </svg>
  );
}

// ─── Variant: pill + plus (medical) ───────────────────────────
function LogoMarkPlus({ size = 160, stroke = '#fff' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 200 200" fill="none">
      <g transform="rotate(-45 100 100)">
        <rect x="30" y="65" width="140" height="70" rx="35"
          stroke={stroke} strokeWidth={14} fill="none" />
      </g>
      <g stroke={stroke} strokeWidth={16} strokeLinecap="round">
        <line x1="100" y1="78" x2="100" y2="122" />
        <line x1="78" y1="100" x2="122" y2="100" />
      </g>
    </svg>
  );
}

// ─── Variant: pill + speech bubble (consultation) ─────────────
function LogoMarkChat({ size = 160, stroke = '#fff' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 200 200" fill="none">
      <g transform="rotate(-45 100 100)">
        <rect x="30" y="65" width="140" height="70" rx="35"
          stroke={stroke} strokeWidth={14} fill="none" />
        <line x1="100" y1="65" x2="100" y2="135"
          stroke={stroke} strokeWidth={14} strokeLinecap="round" />
      </g>
      {/* Three dots inside */}
      <g fill={stroke}>
        <circle cx="80" cy="100" r="6" />
        <circle cx="100" cy="100" r="6" />
        <circle cx="120" cy="100" r="6" />
      </g>
    </svg>
  );
}

// ─── Wordmark: "PillMate" set in Pretendard JP Bold ──────────
function Wordmark({ color = '#171717', size = 36 }) {
  return (
    <div style={{
      fontFamily: 'var(--font-sans)',
      fontWeight: 800,
      fontSize: size,
      letterSpacing: '-0.035em',
      color,
      lineHeight: 1,
      display: 'inline-flex',
      alignItems: 'baseline',
    }}>
      <span>Pill</span><span style={{ fontWeight: 500, opacity: 0.92, marginLeft: '0.02em' }}>Mate</span>
    </div>
  );
}

Object.assign(window, { LogoMark, LogoMarkHeart, LogoMarkPlus, LogoMarkChat, Wordmark });
