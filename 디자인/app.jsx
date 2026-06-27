// PillMate — main screens
// All screens are 390×844 iOS frames placed inside a design canvas.

const { useState } = React;

// ─── Design tokens (semantic shortcuts) ───────────────────────────
const C = {
  bg: '#ffffff',
  bgAlt: 'var(--bg-alt)',
  text: 'var(--label-normal)',
  muted: 'var(--label-neutral)',
  alt: 'var(--label-alternative)',
  assist: 'var(--label-assistive)',
  line: 'var(--line-normal)',
  fill: 'var(--fill-normal)',
  fillStrong: 'var(--fill-strong)',
  primary: 'var(--primary-normal)',
  primaryBg: 'var(--c-blue-95)',
  primarySoft: 'var(--c-blue-99)',
  positive: 'var(--status-positive)',
  positiveBg: 'var(--c-green-95)',
  cautionary: 'var(--status-cautionary)',
  cautionaryBg: 'var(--c-orange-95)',
  negative: 'var(--status-negative)',
  negativeBg: 'var(--c-red-95)',
};

// ─── Tiny icon set (24×24, currentColor stroke) ───────────────────
const Icon = ({ name, size = 24, stroke = 1.8, fill = 'none' }) => {
  const paths = {
    pill: <g><path d="M8 6L18 16a4 4 0 11-6 6L2 12a4 4 0 015.7-5.7z"/><path d="M7.2 12.8l5-5"/></g>,
    camera: <g><rect x="3" y="6" width="18" height="14" rx="2"/><circle cx="12" cy="13" r="4"/><path d="M9 6l1.5-2h3L15 6"/></g>,
    rx: <g><path d="M5 3h10l4 4v14a0 0 0 010 0H5a0 0 0 010 0V3a0 0 0 010 0z"/><path d="M14 3v5h5"/><path d="M12 12v6M9 15h6"/></g>,
    chart: <g><path d="M4 20V10"/><path d="M10 20V4"/><path d="M16 20v-7"/><path d="M22 20H2"/></g>,
    chat: <g><path d="M4 5h16a1 1 0 011 1v11a1 1 0 01-1 1h-9l-5 4v-4H4a1 1 0 01-1-1V6a1 1 0 011-1z"/></g>,
    people: <g><circle cx="9" cy="8" r="3.4"/><path d="M2 20c0-3.6 3.1-6 7-6s7 2.4 7 6"/><circle cx="18" cy="9" r="2.6"/><path d="M22 18.5c0-2.6-2-4.5-4.5-4.5"/></g>,
    home: <g><path d="M3 11l9-7 9 7"/><path d="M5 10v9a1 1 0 001 1h12a1 1 0 001-1v-9"/><path d="M10 20v-5h4v5"/></g>,
    bell: <g><path d="M6 9a6 6 0 0112 0c0 4 1.5 5.5 2.5 6.5H3.5C4.5 14.5 6 13 6 9z"/><path d="M10 19a2 2 0 004 0"/></g>,
    plus: <g><path d="M12 5v14M5 12h14"/></g>,
    check: <g><path d="M4 12l5 5L20 6"/></g>,
    clock: <g><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></g>,
    chevronR: <g><path d="M9 6l6 6-6 6"/></g>,
    chevronL: <g><path d="M15 6l-6 6 6 6"/></g>,
    chevronD: <g><path d="M6 9l6 6 6-6"/></g>,
    close: <g><path d="M6 6l12 12M18 6L6 18"/></g>,
    search: <g><circle cx="11" cy="11" r="7"/><path d="M16.5 16.5L21 21"/></g>,
    warn: <g><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18v.5"/></g>,
    info: <g><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 8v.5"/></g>,
    sparkle: <g><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"/><path d="M19 17l.7 2 2 .7-2 .7L19 23l-.7-2-2-.7 2-.7L19 17z"/></g>,
    send: <g><path d="M3 11L21 3l-7 18-3-8-8-2z"/></g>,
    image: <g><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/></g>,
    qr: <g><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 14h3v3h-3zM20 14v3M14 20h3M17 17h4M20 20v1"/></g>,
    flash: <g><path d="M13 2L4 14h7l-1 8 9-12h-7l1-8z"/></g>,
    pin: <g><path d="M12 17v5"/><path d="M9 4h6l1 7c-1.2.5-2.5.8-4 .8s-2.8-.3-4-.8l1-7z"/><path d="M7 13c1.5.5 3 .8 5 .8s3.5-.3 5-.8"/></g>,
    pencil: <g><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 113 3L7 19l-4 1 1-4L16.5 3.5z"/></g>,
    settings: <g><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 01-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.1A1.7 1.7 0 004.6 9a1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 014 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 010 4h-.1a1.7 1.7 0 00-1.5 1z"/></g>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke="currentColor"
      strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
      {paths[name] || null}
    </svg>
  );
};

// ─── Shared shell helpers ─────────────────────────────────────────
const screenStyle = {
  width: '100%', height: '100%', background: C.bg,
  display: 'flex', flexDirection: 'column',
  paddingTop: 54, // status bar room
};
const padX = { paddingLeft: 20, paddingRight: 20 };

// Top bar (custom — replaces IOSNavBar so we control type)
function TopBar({ left, title, right, sub }) {
  return (
    <div style={{
      ...padX, paddingTop: 8, paddingBottom: 12,
      display: 'flex', alignItems: 'center', gap: 8,
      background: C.bg,
    }}>
      <div style={{ width: 32, height: 32, display: 'flex', alignItems: 'center', color: C.text }}>{left}</div>
      <div style={{ flex: 1, textAlign: 'center' }}>
        <div style={{
          fontSize: 17, fontWeight: 700, color: C.text,
          letterSpacing: '-0.012em', lineHeight: '22px',
        }}>{title}</div>
        {sub ? <div style={{ fontSize: 12, color: C.alt, marginTop: 2 }}>{sub}</div> : null}
      </div>
      <div style={{ width: 32, height: 32, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', color: C.text }}>{right}</div>
    </div>
  );
}

// Bottom tab bar — used on Home, Schedule, Chat, Report, Group
function TabBar({ active = 'home' }) {
  const tabs = [
    { id: 'home', icon: 'home', label: '홈' },
    { id: 'schedule', icon: 'clock', label: '복약' },
    { id: 'register', icon: 'rx', label: '처방전', primary: true },
    { id: 'chat', icon: 'chat', label: '상담' },
    { id: 'group', icon: 'people', label: '그룹' },
  ];
  return (
    <div style={{
      position: 'relative', zIndex: 5,
      display: 'flex', justifyContent: 'space-around', alignItems: 'flex-start',
      paddingTop: 10, paddingBottom: 38, // 34px home-indicator + 4px breathing room
      background: '#fff',
      borderTop: `1px solid ${C.line}`,
      boxShadow: '0 -6px 18px rgba(23,23,23,0.04)',
    }}>
      {tabs.map(t => {
        const on = t.id === active;
        if (t.primary) {
          return (
            <div key={t.id} style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
            }}>
              <div style={{
                width: 56, height: 56, marginTop: -28, borderRadius: 9999,
                background: C.primary, color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: '0 8px 20px rgba(0,102,255,0.38), 0 0 0 4px #fff',
              }}>
                <Icon name={t.icon} size={26} stroke={2.2} />
              </div>
              <div style={{
                marginTop: 2, fontSize: 11, fontWeight: 700, color: C.primary,
                letterSpacing: '0.02em',
              }}>{t.label}</div>
            </div>
          );
        }
        return (
          <div key={t.id} style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
            color: on ? C.primary : 'var(--c-coolNeutral-60)',
            minWidth: 48,
          }}>
            <Icon name={t.icon} size={26} stroke={on ? 2.2 : 1.8} />
            <div style={{
              fontSize: 11, fontWeight: on ? 700 : 500,
              letterSpacing: '0.02em',
            }}>{t.label}</div>
          </div>
        );
      })}
    </div>
  );
}

// "Pill" — colored capsule placeholder used as a drug visual
function PillVisual({ color = 'lightBlue', size = 44 }) {
  const palette = {
    lightBlue: ['#A1E1FF', '#FFFFFF'],
    orange:    ['#FFC06E', '#FFE6C6'],
    pink:      ['#FFB8F3', '#FED3F7'],
    violet:    ['#C0B0FF', '#DBD3FE'],
    green:     ['#ACFCC7', '#FFFFFF'],
    yellow:    ['#FFE074', '#FEF4A8'],
    white:     ['#FFFFFF', '#E1E2E4'],
    blue:      ['#9EC5FF', '#C9DEFE'],
  };
  const [a, b] = palette[color] || palette.lightBlue;
  const w = size, h = size * 0.62;
  return (
    <div style={{
      width: w, height: h, borderRadius: h / 2, overflow: 'hidden',
      position: 'relative', flexShrink: 0,
      boxShadow: 'inset 0 -2px 0 rgba(0,0,0,0.06), 0 1px 2px rgba(23,23,23,0.08)',
    }}>
      <div style={{ position: 'absolute', inset: 0, left: 0, width: '50%', background: a }} />
      <div style={{ position: 'absolute', inset: 0, left: '50%', width: '50%', background: b }} />
      <div style={{
        position: 'absolute', left: '50%', top: 2, bottom: 2, width: 1,
        background: 'rgba(0,0,0,0.08)',
      }} />
    </div>
  );
}

// Avatar — initial on tinted circle
function Avatar({ name = '김', tint = '#0066FF', size = 40 }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: tint, color: '#fff',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: size * 0.42, fontWeight: 700, letterSpacing: '-0.02em',
      flexShrink: 0,
    }}>{name}</div>
  );
}

// Export everything for other JSX files
Object.assign(window, { C, Icon, TopBar, TabBar, PillVisual, Avatar, screenStyle, padX });
