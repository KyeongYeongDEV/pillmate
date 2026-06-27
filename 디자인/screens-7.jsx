// PillMate — Screens part 7: Login + Prescription history

// ────────────────────────────────────────────────────────────────
// 14. Login — social auth (Kakao / Google / Naver)
// ────────────────────────────────────────────────────────────────
function ScreenLogin() {
  return (
    <div style={{ ...screenStyle, paddingTop: 0, background: '#fff' }}>
      <div style={{ height: 54 }} />

      {/* hero */}
      <div style={{ flex: 1, ...padX, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', paddingBottom: 20 }}>
        {/* logo block */}
        <div style={{
          width: 88, height: 88, borderRadius: 22,
          background: C.primary, color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: '0 16px 32px rgba(0,102,255,0.28)'
        }}>
          {/* Logo mark — pill capsule shape */}
          <svg width="56" height="56" viewBox="0 0 200 200" fill="none">
            <g transform="rotate(-45 100 100)">
              <rect x="30" y="65" width="140" height="70" rx="35"
              stroke="#fff" strokeWidth={14} fill="none" />
              <line x1="100" y1="65" x2="100" y2="135"
              stroke="#fff" strokeWidth={14} strokeLinecap="round" />
              <circle cx="55" cy="100" r="9" fill="#fff" />
              <circle cx="145" cy="100" r="9" fill="#fff" />
            </g>
          </svg>
        </div>

        <div style={{
          fontFamily: 'var(--font-sans)', fontWeight: 800, fontSize: 38,
          letterSpacing: '-0.035em', color: C.text, marginTop: 24
        }}>
          <span>Pill</span><span style={{ fontWeight: 500, opacity: 0.92 }}>Mate</span>
        </div>
        <div style={{ fontSize: 15, color: C.muted, marginTop: 10, textAlign: 'center', lineHeight: '22px' }}>
          처방전 한 장으로<br />온 가족의 복약을 함께 관리해요
        </div>
      </div>

      {/* social buttons */}
      <div style={{ paddingLeft: 32, paddingRight: 32, paddingBottom: 28 }}>
        <div style={{ fontSize: 12, color: C.alt, textAlign: 'center', marginBottom: 16, fontWeight: 500 }}>
          간편 로그인으로 시작하세요
        </div>

        {/* Kakao */}
        <SocialButton
          bg="#FEE500" fg="#191919"
          label="카카오로 계속하기"
          tag="3초 만에 시작"
          icon={
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M10 3C5.58 3 2 5.74 2 9.12c0 2.16 1.47 4.06 3.7 5.15-.16.59-.59 2.18-.67 2.52-.1.42.16.41.33.3.13-.09 2.13-1.45 2.97-2.02.55.08 1.12.12 1.67.12 4.42 0 8-2.74 8-6.12S14.42 3 10 3z"
            fill="#191919" />
            </svg>
          } />
        

        {/* Naver */}
        <SocialButton
          bg="#03C75A" fg="#fff"
          label="네이버로 계속하기"
          icon={
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M11.5 10.6L8.4 6H5v8h3.5V9.4L11.6 14H15V6h-3.5v4.6z" fill="#fff" />
            </svg>
          } />
        

        {/* Google */}
        <SocialButton
          bg="#fff" fg="#171717" border
          label="Google로 계속하기"
          icon={
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M19.6 10.23c0-.68-.06-1.36-.18-2.02H10v3.82h5.4a4.62 4.62 0 01-2 3.04v2.51h3.23c1.89-1.74 2.97-4.3 2.97-7.35z" fill="#4285F4" />
              <path d="M10 20c2.7 0 4.96-.9 6.62-2.42l-3.23-2.51c-.9.6-2.04.96-3.39.96-2.6 0-4.81-1.76-5.6-4.12H1.06v2.59A10 10 0 0010 20z" fill="#34A853" />
              <path d="M4.4 11.91A6.02 6.02 0 014.08 10c0-.66.11-1.3.32-1.91V5.5H1.06A10 10 0 000 10c0 1.61.39 3.14 1.06 4.5L4.4 11.9z" fill="#FBBC05" />
              <path d="M10 3.97c1.47 0 2.78.51 3.82 1.5l2.86-2.86A10 10 0 0010 0a10 10 0 00-8.94 5.5L4.4 8.09C5.19 5.73 7.4 3.97 10 3.97z" fill="#EA4335" />
            </svg>
          } />
        

        {/* divider */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '20px 0' }}>
          <div style={{ flex: 1, height: 1, background: C.line }} />
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>또는</div>
          <div style={{ flex: 1, height: 1, background: C.line }} />
        </div>

        {/* email */}
        <div style={{ textAlign: 'center', fontSize: 14, color: C.text, fontWeight: 600 }}>
          이메일로 시작하기
        </div>

        {/* terms */}
        <div style={{ fontSize: 11, color: C.assist, textAlign: 'center', marginTop: 28, lineHeight: '17px' }}>
          가입 시 <span style={{ color: C.muted, textDecoration: 'underline' }}>이용약관</span>과 <span style={{ color: C.muted, textDecoration: 'underline' }}>개인정보 처리방침</span>에 동의한 것으로 간주됩니다.
        </div>
      </div>
    </div>);

}

function SocialButton({ bg, fg, label, tag, icon, border }) {
  return (
    <div style={{
      width: '100%', height: 54, borderRadius: 12,
      background: bg, color: fg,
      border: border ? `1px solid ${C.line}` : 'none',
      display: 'flex', alignItems: 'center', gap: 14,
      padding: '0 60px 0 22px',
      marginBottom: 10, position: 'relative',
    }}>
      {/* Left icon */}
      <div style={{ width: 24, display: 'flex', justifyContent: 'center', flexShrink: 0 }}>{icon}</div>
      {/* Label — left-aligned next to the icon, right side stays empty for breathing room */}
      <div style={{
        fontSize: 16, fontWeight: 600, letterSpacing: '-0.012em',
      }}>{label}</div>
      {tag &&
      <div style={{
        position: 'absolute', top: -8, right: 14,
        padding: '3px 8px', borderRadius: 9999,
        background: C.text, color: '#fff',
        fontSize: 10, fontWeight: 700, letterSpacing: '0.02em'
      }}>{tag}</div>
      }
    </div>);

}

// ────────────────────────────────────────────────────────────────
// 15. Prescription History — list of all registered prescriptions
// ────────────────────────────────────────────────────────────────
function ScreenPrescriptions() {
  const tabs = [
  { id: 'all', label: '전체', n: 12 },
  { id: 'ing', label: '복용중', n: 3, color: C.positive },
  { id: 'done', label: '복용완료', n: 8, color: C.alt },
  { id: 'stop', label: '중단', n: 1, color: C.negative }];


  const groups = [
  {
    title: '복용중',
    items: [
    {
      state: 'ing',
      title: '내과 진료 처방',
      hospital: '서울내과의원 · 김원장',
      date: '2025.11.24',
      range: '21일분 · 11.24 → 12.14',
      progress: 0.05, // day 1 of 21
      progressText: 'D-20',
      drugs: 5,
      pills: ['lightBlue', 'orange', 'pink', 'white', 'yellow'],
      owner: '할', tint: '#FF7B2E', ownerLabel: '할머니',
      fresh: true,
      memo: '식후 30분에 복용 · 어지러움 있으면 다음 진료 때 상담'
    },
    {
      state: 'ing',
      title: '정형외과 처방',
      hospital: '연세정형외과 · 박원장',
      date: '2025.11.10',
      range: '14일분 · 11.10 → 11.24',
      progress: 0.95,
      progressText: 'D-1',
      drugs: 2,
      pills: ['pink', 'violet'],
      owner: '엄', tint: '#0066FF', ownerLabel: '엄마',
      warn: '내일 마지막'
    },
    {
      state: 'ing',
      title: '안과 점안액',
      hospital: '밝은안과의원',
      date: '2025.11.05',
      range: '30일분 · 11.05 → 12.05',
      progress: 0.63,
      progressText: 'D-11',
      drugs: 1,
      pills: ['blue'],
      owner: '할', tint: '#FF7B2E', ownerLabel: '할머니'
    }]

  },
  {
    title: '복용완료',
    items: [
    {
      state: 'done',
      title: '감기 증상 처방',
      hospital: '서울내과의원',
      date: '2025.10.18',
      range: '5일분 · 10.18 → 10.23',
      progress: 1,
      drugs: 3,
      pills: ['pink', 'white', 'green'],
      owner: '엄', tint: '#0066FF', ownerLabel: '엄마',
      adherence: 100
    },
    {
      state: 'done',
      title: '내과 진료 처방',
      hospital: '서울내과의원 · 김원장',
      date: '2025.10.03',
      range: '21일분 · 10.03 → 10.24',
      progress: 1,
      drugs: 4,
      pills: ['lightBlue', 'orange', 'pink', 'white'],
      owner: '할', tint: '#FF7B2E', ownerLabel: '할머니',
      adherence: 92,
      memo: '혈압 안정됨 — 다음 처방까지 동일 유지'
    },
    {
      state: 'stop',
      title: '소화제 처방',
      hospital: '동대문가정의학과',
      date: '2025.09.21',
      range: '7일분 · 9.21 → 9.28',
      progress: 0.4,
      drugs: 2,
      pills: ['yellow', 'green'],
      owner: '아', tint: '#6541F2', ownerLabel: '아빠',
      stopReason: '증상 호전으로 중단'
    }]

  }];


  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="처방전"
        sub="총 12건 · 복용중 3"
        right={<Icon name="search" size={22} stroke={1.8} />} />
      

      {/* filter chips */}
      <div style={{
        ...padX, paddingTop: 4, paddingBottom: 12,
        display: 'flex', gap: 6, background: C.bg,
        borderBottom: `1px solid ${C.line}`,
        overflowX: 'auto'
      }}>
        {tabs.map((t, i) => {
          const on = i === 0;
          return (
            <div key={t.id} style={{
              padding: '8px 14px', borderRadius: 9999,
              background: on ? C.text : C.fill,
              color: on ? '#fff' : C.muted,
              fontSize: 13, fontWeight: on ? 700 : 500,
              display: 'flex', alignItems: 'center', gap: 6,
              flexShrink: 0
            }}>
              {!on && t.color && <div style={{ width: 6, height: 6, borderRadius: '50%', background: t.color }} />}
              {t.label}
              <span style={{
                fontSize: 11, padding: '1px 6px', borderRadius: 9999,
                background: on ? 'rgba(255,255,255,0.22)' : '#fff',
                color: on ? '#fff' : C.alt, fontWeight: 700
              }}>{t.n}</span>
            </div>);

        })}
      </div>

      {/* list */}
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 90 }}>
        {groups.map((g, gi) =>
        <div key={gi} style={{ paddingTop: gi === 0 ? 16 : 24 }}>
            <div style={{ ...padX, paddingBottom: 10, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                {g.title} · {g.items.length}
              </div>
            </div>
            <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {g.items.map((p, i) => <PrescriptionCard key={i} p={p} />)}
            </div>
          </div>
        )}

        <div style={{ padding: '24px 20px 16px', textAlign: 'center', fontSize: 12, color: C.assist }}>
          최근 1년 처방전만 표시됩니다
        </div>
      </div>

      {/* FAB */}
      <div style={{
        position: 'absolute', right: 20, bottom: 28, zIndex: 5,
        height: 56, borderRadius: 9999, background: C.text, color: '#fff',
        display: 'flex', alignItems: 'center', gap: 6, padding: '0 22px',
        boxShadow: '0 12px 24px rgba(23,23,23,0.28)'
      }}>
        <Icon name="camera" size={22} stroke={2} />
        <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: '-0.012em' }}>처방전 추가</div>
      </div>
    </div>);

}

function PrescriptionCard({ p }) {
  const label = {
    ing: { text: '복용중', bg: 'var(--c-green-95)', fg: 'var(--c-green-40)', dot: C.positive },
    done: { text: '복용완료', bg: C.fill, fg: C.muted, dot: C.alt },
    stop: { text: '중단', bg: 'var(--c-red-95)', fg: 'var(--c-red-40)', dot: C.negative }
  }[p.state];

  return (
    <div style={{
      background: '#fff', borderRadius: 16,
      border: `1px solid ${p.fresh ? 'var(--c-blue-90)' : C.line}`,
      padding: 16, position: 'relative',
      boxShadow: p.fresh ? '0 6px 18px rgba(0,102,255,0.08)' : 'none'
    }}>
      {/* fresh badge */}
      {p.fresh &&
      <div style={{
        position: 'absolute', top: -8, right: 14,
        padding: '3px 8px', borderRadius: 9999,
        background: C.primary, color: '#fff',
        fontSize: 10, fontWeight: 700, letterSpacing: '0.02em'
      }}>NEW</div>
      }

      {/* header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        {/* prescription thumb */}
        <div style={{
          width: 44, height: 56, borderRadius: 6,
          background: '#F4F1EA', flexShrink: 0, position: 'relative', overflow: 'hidden',
          border: `1px solid ${C.line}`
        }}>
          {[8, 14, 20, 26, 32, 38, 44].map((y) =>
          <div key={y} style={{ position: 'absolute', left: 5, right: 5, top: y, height: 1.5, background: '#D0CABE' }} />
          )}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '3px 8px', borderRadius: 6,
              background: label.bg, color: label.fg,
              fontSize: 11, fontWeight: 700
            }}>
              <div style={{ width: 5, height: 5, borderRadius: '50%', background: label.dot }} />
              {label.text}
            </div>
            <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>· {p.date}</div>
          </div>
          <div style={{ fontSize: 15, fontWeight: 700, color: C.text, marginTop: 6, letterSpacing: '-0.012em' }}>
            {p.title}
          </div>
          <div style={{ fontSize: 12, color: C.muted, marginTop: 1 }}>{p.hospital}</div>
        </div>

        {/* owner avatar */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <Avatar name={p.owner} tint={p.tint} size={32} />
          <div style={{ fontSize: 10, color: C.alt, fontWeight: 600 }}>{p.ownerLabel}</div>
        </div>
      </div>

      {/* progress + range */}
      <div style={{ marginTop: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
          <div style={{ fontSize: 12, color: C.muted, fontWeight: 500 }}>{p.range}</div>
          {p.state === 'ing' &&
          <div style={{
            fontSize: 11, fontWeight: 700,
            color: p.warn ? C.cautionary : C.positive
          }}>{p.warn || p.progressText}</div>
          }
          {p.state === 'done' &&
          <div style={{ fontSize: 11, fontWeight: 700, color: C.alt }}>
              복약률 {p.adherence}%
            </div>
          }
          {p.state === 'stop' &&
          <div style={{ fontSize: 11, fontWeight: 600, color: C.negative }}>{p.stopReason}</div>
          }
        </div>
        <div style={{ height: 6, borderRadius: 3, background: 'var(--fill-strong)', overflow: 'hidden' }}>
          <div style={{
            height: '100%', borderRadius: 3,
            width: `${p.progress * 100}%`,
            background: p.state === 'stop' ? C.negative : p.state === 'done' ? C.alt : C.primary
          }} />
        </div>
      </div>

      {/* Memo (if any) */}
      {p.memo && (
        <div style={{
          marginTop: 12, padding: '10px 12px', borderRadius: 10,
          background: 'var(--c-yellow-95, #FEF4A8)',
          borderLeft: '3px solid var(--c-yellow-50, #D7B33A)',
          display: 'flex', gap: 8, alignItems: 'flex-start',
        }}>
          <Icon name="pencil" size={13} stroke={2} />
          <div style={{ flex: 1, fontSize: 12, color: '#5a4a2a', lineHeight: '17px', fontWeight: 500 }}>
            {p.memo}
          </div>
        </div>
      )}

      {/* footer: pills + count */}
      <div style={{ display: 'flex', alignItems: 'center', marginTop: 14 }}>
        <div style={{ display: 'flex' }}>
          {p.pills.slice(0, 5).map((c, i) =>
          <div key={i} style={{ marginLeft: i === 0 ? 0 : -8 }}>
              <PillVisual color={c} size={26} />
            </div>
          )}
        </div>
        <div style={{ flex: 1, fontSize: 12, color: C.muted, marginLeft: 10, fontWeight: 500 }}>
          약 {p.drugs}개
        </div>
        <div style={{
          padding: '6px 11px', borderRadius: 8,
          background: C.fill, color: C.text,
          fontSize: 12, fontWeight: 600,
          display: 'flex', alignItems: 'center', gap: 3
        }}>
          상세 <Icon name="chevronR" size={14} stroke={2.2} />
        </div>
      </div>
    </div>);

}

Object.assign(window, { ScreenLogin, ScreenPrescriptions });