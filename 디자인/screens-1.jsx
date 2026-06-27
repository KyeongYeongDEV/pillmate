// PillMate — Screens part 1: Onboarding · Home · Scan · Result

// ────────────────────────────────────────────────────────────────
// 1. Onboarding — first-run "value prop"
// ────────────────────────────────────────────────────────────────
function ScreenOnboarding() {
  return (
    <div style={{ ...screenStyle, paddingTop: 0, background: '#fff' }}>
      <div style={{ height: 54 }} />
      {/* top: skip + dot indicator */}
      <div style={{ ...padX, display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingTop: 8, paddingBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: C.primary, fontWeight: 700, fontSize: 17, letterSpacing: '-0.02em' }}>
          <div style={{ width: 26, height: 26, borderRadius: 8, background: C.primary, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="pill" size={16} stroke={2.2} />
          </div>
          PillMate
        </div>
        <div style={{ color: C.alt, fontSize: 14, fontWeight: 500 }}>건너뛰기</div>
      </div>

      {/* hero visual: layered prescription + AI card */}
      <div style={{ ...padX, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
        <div style={{
          position: 'relative', height: 320, marginTop: 12,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {/* prescription paper */}
          <div style={{
            width: 220, height: 280, background: '#fff',
            borderRadius: 12, transform: 'rotate(-6deg)',
            boxShadow: 'var(--shadow-large)', border: `1px solid ${C.line}`,
            padding: '20px 18px',
          }}>
            <div style={{ fontSize: 10, color: C.alt, letterSpacing: '0.05em' }}>처방전 · Rx</div>
            <div style={{ fontSize: 13, fontWeight: 700, marginTop: 4, color: C.text }}>박○○ · 만 72세</div>
            <div style={{ height: 1, background: C.line, margin: '12px 0' }} />
            {['암로디핀 5mg', '메트포르민 500mg', '아토르바스타틴 10mg', '글리메피리드 2mg'].map(d => (
              <div key={d} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 0', fontSize: 11, color: C.muted }}>
                <div style={{ width: 5, height: 5, borderRadius: '50%', background: C.text }} />
                {d}
              </div>
            ))}
          </div>
          {/* AI confirmation card overlay */}
          <div style={{
            position: 'absolute', bottom: 14, right: 4,
            width: 220, padding: '14px 16px',
            background: '#fff', borderRadius: 16,
            boxShadow: 'var(--shadow-xlarge)', border: `1px solid ${C.line}`,
            transform: 'rotate(3deg)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: C.primary, fontSize: 11, fontWeight: 600 }}>
              <Icon name="sparkle" size={14} stroke={2} fill="currentColor" />
              AI · 1.4초 만에 인식
            </div>
            <div style={{ fontSize: 15, fontWeight: 700, marginTop: 8, color: C.text, letterSpacing: '-0.015em' }}>
              4개 약 자동 등록됨
            </div>
            <div style={{ display: 'flex', gap: 4, marginTop: 10 }}>
              {['lightBlue', 'orange', 'pink', 'white'].map(c => <PillVisual key={c} color={c} size={28} />)}
            </div>
          </div>
        </div>

        <div>
          <div style={{ fontSize: 32, fontWeight: 700, color: C.text, letterSpacing: '-0.025em', lineHeight: '42px' }}>
            처방전 한 장으로<br/>온 가족 복약 관리
          </div>
          <div style={{ fontSize: 15, color: C.muted, marginTop: 12, lineHeight: '24px', letterSpacing: '0.005em' }}>
            사진만 찍으면 AI가 약을 인식해 자동 등록합니다.<br/>
            식약처 데이터로 검증된 안전한 복약 정보를 받아보세요.
          </div>

          {/* dots */}
          <div style={{ display: 'flex', gap: 6, marginTop: 32 }}>
            {[0, 1, 2].map(i => (
              <div key={i} style={{
                width: i === 0 ? 22 : 6, height: 6, borderRadius: 3,
                background: i === 0 ? C.text : 'var(--label-assistive)',
                transition: 'all .2s',
              }} />
            ))}
          </div>

          <div style={{ marginTop: 16, marginBottom: 28 }}>
            <div style={{
              height: 56, borderRadius: 12, background: C.text, color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 17, fontWeight: 700, letterSpacing: '-0.012em',
            }}>다음</div>
            <div style={{ textAlign: 'center', marginTop: 14, fontSize: 14, color: C.alt }}>
              이미 계정이 있어요 · <span style={{ color: C.text, fontWeight: 600 }}>로그인</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 2. Home — today's medication + family activity
// ────────────────────────────────────────────────────────────────
function ScreenHome() {
  const slots = [
    { time: '아침 · 8:00', state: 'done', count: 2, pills: ['lightBlue', 'orange'] },
    { time: '점심 · 12:30', state: 'now', count: 2, pills: ['pink', 'white'] },
    { time: '저녁 · 19:00', state: 'wait', count: 1, pills: ['violet'] },
    { time: '취침 전 · 22:00', state: 'wait', count: 1, pills: ['blue'] },
  ];
  const activity = [
    { who: '할머니', tint: '#FF7B2E', text: '아침약 2개를 복용했어요', time: '8:12', icon: 'check' },
    { who: '엄마', tint: '#6541F2', text: '새 처방전을 등록했어요', time: '7:40', icon: 'plus' },
    { who: '시스템', tint: '#878A93', text: '내일 처방 1일 남았어요', time: '7:00', icon: 'bell' },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      {/* header */}
      <div style={{ ...padX, paddingTop: 12, paddingBottom: 16, background: C.bg }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: C.muted, fontWeight: 600 }}>
            할머니 댁 · 3명
            <Icon name="chevronD" size={16} stroke={2.2} />
          </div>
          <div style={{
            position: 'relative',
            width: 40, height: 40, borderRadius: 12,
            background: C.fill,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: C.text,
          }}>
            <Icon name="bell" size={22} stroke={1.9} />
            <div style={{
              position: 'absolute', top: 4, right: 4,
              minWidth: 16, height: 16, padding: '0 4px',
              borderRadius: 9999, background: C.negative, color: '#fff',
              border: '1.5px solid #fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 10, fontWeight: 700, fontVariantNumeric: 'tabular-nums',
            }}>4</div>
          </div>
        </div>
        <div style={{ marginTop: 12, fontSize: 26, fontWeight: 700, letterSpacing: '-0.022em', color: C.text }}>
          안녕하세요, 민지님
        </div>
        <div style={{ fontSize: 14, color: C.muted, marginTop: 2 }}>오늘 할머니 복약 4/6 완료</div>

        {/* progress */}
        <div style={{ marginTop: 16, height: 8, borderRadius: 4, background: 'var(--fill-strong)', overflow: 'hidden' }}>
          <div style={{ width: '67%', height: '100%', background: C.primary, borderRadius: 4 }} />
        </div>
      </div>

      {/* body */}
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 8 }}>
        {/* section: today */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.015em' }}>오늘의 복약</div>
          <div style={{ fontSize: 13, color: C.alt, fontWeight: 600 }}>11월 24일 월</div>
        </div>

        <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {slots.map((s, i) => {
            const isDone = s.state === 'done';
            const isNow = s.state === 'now';
            return (
              <div key={i} style={{
                background: isDone ? '#fff' : 'var(--bg-alt)',
                borderRadius: 16,
                border: isDone
                  ? `1.5px solid var(--c-green-90)`
                  : isNow ? `1.5px solid ${C.primary}` : `1px solid ${C.line}`,
                boxShadow: isDone
                  ? '0 6px 18px rgba(38, 199, 108, 0.10)'
                  : isNow ? '0 4px 12px rgba(0,102,255,0.06)' : 'none',
                padding: 16, display: 'flex', alignItems: 'center', gap: 14,
              }}>
                {/* Tap target — empty circle (pending) → filled green check (done) */}
                <div style={{
                  width: 40, height: 40, borderRadius: '50%',
                  background: isDone ? C.positive : '#fff',
                  border: isDone ? 'none' : `2px solid var(--label-assistive)`,
                  color: isDone ? '#fff' : 'transparent',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}>
                  {isDone && <Icon name="check" size={22} stroke={2.6} />}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{
                    fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase',
                    color: isDone ? 'var(--c-green-40)' : 'var(--label-assistive)',
                  }}>
                    {s.time}
                  </div>
                  <div style={{
                    fontSize: 16, fontWeight: 700, marginTop: 2, letterSpacing: '-0.012em',
                    color: isDone ? 'var(--c-green-30)' : isNow ? C.text : 'var(--label-neutral)',
                  }}>
                    {isDone ? '복용 완료' : isNow ? '지금 드세요' : '복용 대기'}
                  </div>
                </div>
                {/* Pills — show only the first one. Full color when done, desaturated when pending */}
                <div style={{ display: 'flex', filter: isDone ? 'none' : 'grayscale(0.6) opacity(0.55)' }}>
                  <PillVisual color={s.pills[0]} size={32} />
                </div>
              </div>
            );
          })}
        </div>

        {/* AI insight card */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 12 }}>
          <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.015em' }}>AI 인사이트</div>
        </div>
        <div style={padX}>
          <div style={{
            background: '#fff', borderRadius: 16, padding: 18,
            border: `1px solid ${C.line}`,
            display: 'flex', gap: 14, alignItems: 'flex-start',
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: 'var(--c-violet-95)', color: 'var(--c-violet-45)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon name="sparkle" size={20} stroke={1.8} fill="currentColor" />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em', lineHeight: '22px' }}>
                저녁약을 3일째 빠뜨렸어요
              </div>
              <div style={{ fontSize: 13, color: C.muted, marginTop: 4, lineHeight: '20px' }}>
                메트포르민을 거르면 혈당 조절이 어려워질 수 있어요. 알림 시간을 옮겨볼까요?
              </div>
              <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
                <div style={{ padding: '6px 12px', borderRadius: 8, background: C.text, color: '#fff', fontSize: 13, fontWeight: 600 }}>알림 조정</div>
                <div style={{ padding: '6px 12px', borderRadius: 8, background: C.fill, color: C.muted, fontSize: 13, fontWeight: 600 }}>나중에</div>
              </div>
            </div>
          </div>
        </div>

        {/* activity feed */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.015em' }}>가족 활동</div>
          <div style={{ fontSize: 13, color: C.primary, fontWeight: 600 }}>전체보기</div>
        </div>
        <div style={{ ...padX, background: 'transparent' }}>
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, overflow: 'hidden' }}>
            {activity.map((a, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '14px 16px',
                borderTop: i === 0 ? 'none' : `1px solid ${C.line}`,
              }}>
                <Avatar name={a.who[0]} tint={a.tint} size={36} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, color: C.text, lineHeight: '20px' }}>
                    <span style={{ fontWeight: 700 }}>{a.who}</span>
                    <span style={{ color: C.muted }}>이(가) {a.text}</span>
                  </div>
                  <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>{a.time}</div>
                </div>
              </div>
            ))}
          </div>
          <div style={{ height: 12 }} />
        </div>
      </div>

      <TabBar active="home" />
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 3. Prescription Scan — camera UI
// ────────────────────────────────────────────────────────────────
function ScreenScan() {
  return (
    <div style={{ ...screenStyle, paddingTop: 0, background: '#0F0F10', color: '#fff' }}>
      {/* fake camera background */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'radial-gradient(110% 60% at 50% 35%, #2A2D38 0%, #0F0F10 70%)',
      }} />
      {/* paper preview (slightly off-angle) */}
      <div style={{
        position: 'absolute', top: 200, left: 60, right: 60, bottom: 240,
        background: '#F4F1EA', borderRadius: 4,
        boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
        transform: 'rotate(-1.2deg) perspective(800px) rotateX(4deg)',
        padding: '24px 22px', overflow: 'hidden',
      }}>
        <div style={{ fontSize: 9, color: '#666', letterSpacing: '0.1em' }}>처방전 · PRESCRIPTION</div>
        <div style={{ height: 1, background: '#ccc', margin: '8px 0 10px' }} />
        {[
          ['암로디핀정 5mg', '1정 · 1일 1회'],
          ['메트포르민 500mg', '1정 · 1일 2회'],
          ['아토르바스타틴 10mg', '1정 · 1일 1회'],
          ['글리메피리드 2mg', '1정 · 1일 1회'],
          ['오메가-3', '1정 · 1일 1회'],
        ].map((row, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: 10, color: '#333' }}>
            <span>{i + 1}. {row[0]}</span>
            <span style={{ color: '#666' }}>{row[1]}</span>
          </div>
        ))}
      </div>

      {/* status bar room */}
      <div style={{ height: 54, position: 'relative', zIndex: 5 }} />

      {/* top controls */}
      <div style={{
        ...padX, position: 'relative', zIndex: 5,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        paddingTop: 8, paddingBottom: 12,
      }}>
        <div style={{
          width: 36, height: 36, borderRadius: '50%',
          background: 'rgba(255,255,255,0.18)', backdropFilter: 'blur(20px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="close" size={20} stroke={2.2} />
        </div>
        <div style={{
          padding: '8px 14px', borderRadius: 9999,
          background: 'rgba(255,255,255,0.18)', backdropFilter: 'blur(20px)',
          fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <Icon name="sparkle" size={14} stroke={2} fill="currentColor" />
          AI 자동 인식
        </div>
        <div style={{
          width: 36, height: 36, borderRadius: '50%',
          background: 'rgba(255,255,255,0.18)', backdropFilter: 'blur(20px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="flash" size={20} stroke={2} />
        </div>
      </div>

      {/* frame guide */}
      <div style={{ flex: 1, position: 'relative', zIndex: 4, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ position: 'relative', width: 314, height: 408 }}>
          {/* corner brackets */}
          {[
            { top: 0, left: 0, br: '12px 0 0 0' },
            { top: 0, right: 0, br: '0 12px 0 0' },
            { bottom: 0, left: 0, br: '0 0 0 12px' },
            { bottom: 0, right: 0, br: '0 0 12px 0' },
          ].map((p, i) => (
            <div key={i} style={{
              position: 'absolute', width: 40, height: 40,
              ...p,
              borderRadius: p.br,
              borderTop: i < 2 ? '3px solid #fff' : 'none',
              borderBottom: i >= 2 ? '3px solid #fff' : 'none',
              borderLeft: i % 2 === 0 ? '3px solid #fff' : 'none',
              borderRight: i % 2 === 1 ? '3px solid #fff' : 'none',
            }} />
          ))}
          {/* hint pill */}
          <div style={{
            position: 'absolute', top: -52, left: '50%', transform: 'translateX(-50%)',
            padding: '8px 14px', borderRadius: 9999,
            background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(20px)',
            fontSize: 13, color: '#fff', fontWeight: 500, whiteSpace: 'nowrap',
          }}>
            처방전을 사각형 안에 맞춰주세요
          </div>
        </div>
      </div>

      {/* bottom controls */}
      <div style={{
        position: 'relative', zIndex: 5,
        ...padX, paddingTop: 16, paddingBottom: 42,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        {/* gallery thumbnail */}
        <div style={{
          width: 52, height: 52, borderRadius: 12, overflow: 'hidden',
          border: '2px solid rgba(255,255,255,0.6)',
          background: 'linear-gradient(135deg, #6541F2 0%, #FA73E3 100%)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon name="image" size={22} stroke={2} />
        </div>
        {/* capture */}
        <div style={{
          width: 76, height: 76, borderRadius: '50%',
          background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: '0 0 0 4px rgba(255,255,255,0.25)',
        }}>
          <div style={{ width: 62, height: 62, borderRadius: '50%', background: '#fff', border: '2px solid #0F0F10' }} />
        </div>
        {/* manual */}
        <div style={{
          width: 52, height: 52, borderRadius: 12,
          background: 'rgba(255,255,255,0.18)', backdropFilter: 'blur(20px)',
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          gap: 2,
        }}>
          <Icon name="pencil" size={18} stroke={2} />
          <div style={{ fontSize: 9, fontWeight: 600 }}>수동</div>
        </div>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 4. Recognition Result — confirm extracted drugs
// ────────────────────────────────────────────────────────────────
function ScreenResult() {
  const drugs = [
    { name: '암로디핀정 5mg',     maker: '한미약품',     dose: '1정',   slots: { 아침: true,  점심: false, 저녁: false, 취침전: false }, color: 'lightBlue', conf: 98 },
    { name: '메트포르민 500mg',    maker: '대웅제약',     dose: '1정',   slots: { 아침: true,  점심: false, 저녁: true,  취침전: false }, color: 'orange',    conf: 96 },
    { name: '아토르바스타틴 10mg', maker: '유한양행',     dose: '1정',   slots: { 아침: false, 점심: false, 저녁: true,  취침전: false }, color: 'pink',      conf: 94 },
    { name: '글리메피리드 2mg',    maker: 'JW중외제약',   dose: '1정',   slots: { 아침: true,  점심: false, 저녁: false, 취침전: false }, color: 'white',     conf: 91 },
    { name: '오메가-3 1000mg',     maker: '한국유나이티드', dose: '1캡슐', slots: { 아침: false, 점심: false, 저녁: true,  취침전: false }, color: 'yellow',    conf: 'low' },
  ];
  const defaultTimes = [
    { id: '아침',   time: '08:00', emoji: '🌅' },
    { id: '점심',   time: '12:30', emoji: '🌞' },
    { id: '저녁',   time: '19:00', emoji: '🌇' },
    { id: '취침전', time: '22:00', emoji: '🌙' },
  ];
  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="처방전 인식 결과"
        sub="11월 24일 · 1.4초 만에 인식"
        right={<div style={{ fontSize: 14, color: C.primary, fontWeight: 600 }}>편집</div>}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 100 }}>
        {/* hero summary */}
        <div style={{ ...padX, paddingTop: 8 }}>
          <div style={{
            background: '#fff', borderRadius: 16, padding: 18,
            border: `1px solid ${C.line}`,
            display: 'flex', gap: 14, alignItems: 'center',
          }}>
            {/* prescription thumb */}
            <div style={{
              width: 64, height: 80, borderRadius: 8,
              background: '#F4F1EA', position: 'relative', overflow: 'hidden', flexShrink: 0,
              border: `1px solid ${C.line}`,
            }}>
              {[10, 18, 26, 34, 42, 50, 58].map(y => (
                <div key={y} style={{ position: 'absolute', left: 8, right: 8, top: y, height: 2, background: '#D0CABE' }} />
              ))}
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '3px 8px', borderRadius: 6, background: 'var(--c-violet-95)', color: 'var(--c-violet-45)', fontSize: 11, fontWeight: 600 }}>
                <Icon name="sparkle" size={11} stroke={2} fill="currentColor" />
                Gemini Vision · RAG 매칭
              </div>
              <div style={{ fontSize: 17, fontWeight: 700, marginTop: 6, letterSpacing: '-0.012em' }}>약 5개 추출됨</div>
              <div style={{ fontSize: 13, color: C.muted, marginTop: 2 }}>4개 정상 · <span style={{ color: C.cautionary, fontWeight: 600 }}>1개 확인 필요</span></div>
            </div>
          </div>
        </div>

        {/* Default times card */}
        <div style={{ ...padX, paddingTop: 20 }}>
          <div style={{
            background: '#fff', borderRadius: 16, padding: 16,
            border: `1px solid ${C.line}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <Icon name="clock" size={16} stroke={2} />
              <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em', flex: 1 }}>기본 복용 시간</div>
              <div style={{ fontSize: 11, color: C.alt }}>탭해서 수정</div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
              {defaultTimes.map(t => (
                <div key={t.id} style={{
                  borderRadius: 10, padding: '10px 4px',
                  background: 'var(--fill-alt)',
                  border: `1px solid ${C.line}`,
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                }}>
                  <div style={{ fontSize: 16 }}>{t.emoji}</div>
                  <div style={{ fontSize: 11, color: C.alt, fontWeight: 600 }}>{t.id}</div>
                  <div style={{ fontSize: 13, color: C.text, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{t.time}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* drug cards */}
        <div style={{ ...padX, paddingTop: 20, paddingBottom: 10, display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
            인식된 약 · 5
          </div>
          <div style={{ fontSize: 12, color: C.alt }}>탭해서 편집</div>
        </div>
        <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {drugs.map((d, i) => {
            const low = d.conf === 'low';
            const slotIds = ['아침', '점심', '저녁', '취침전'];
            const activeCount = slotIds.filter(s => d.slots[s]).length;
            return (
              <div key={i} style={{
                background: '#fff', borderRadius: 16, padding: 14,
                border: `1px solid ${low ? 'var(--c-orange-90)' : C.line}`,
              }}>
                {/* row 1: pill + name + state */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <PillVisual color={d.color} size={38} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em' }}>{d.name}</div>
                    <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>{d.maker} · {d.dose}</div>
                  </div>
                  {low ? (
                    <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--c-orange-95)', color: 'var(--c-orange-40)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Icon name="warn" size={16} stroke={2} />
                    </div>
                  ) : (
                    <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--c-green-95)', color: 'var(--c-green-40)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Icon name="check" size={18} stroke={2.4} />
                    </div>
                  )}
                </div>

                {/* row 2: slot toggles */}
                <div style={{ marginTop: 12, padding: '10px', background: C.bgAlt, borderRadius: 10 }}>
                  <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, marginBottom: 8 }}>
                    하루 <b style={{ color: C.text, fontWeight: 700 }}>{activeCount}회</b> 복용
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
                    {defaultTimes.map(t => {
                      const on = d.slots[t.id];
                      return (
                        <div key={t.id} style={{
                          borderRadius: 8, padding: '8px 4px',
                          background: on ? C.primary : '#fff',
                          border: on ? 'none' : `1px solid ${C.line}`,
                          color: on ? '#fff' : C.alt,
                          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2,
                        }}>
                          <div style={{ fontSize: 11, fontWeight: on ? 700 : 600, letterSpacing: '-0.005em' }}>{t.id}</div>
                          <div style={{ fontSize: 10, fontWeight: on ? 600 : 500, opacity: on ? 0.85 : 1, fontVariantNumeric: 'tabular-nums' }}>{on ? t.time : '—'}</div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            );
          })}

          {/* Inline add button — appears right under the recognized drug list */}
          <div style={{
            padding: '16px 14px',
            borderRadius: 14, border: `1.5px dashed var(--c-blue-90)`,
            background: 'var(--c-blue-99)',
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: '#fff', color: C.primary,
              border: `1px solid var(--c-blue-90)`,
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon name="plus" size={20} stroke={2.4} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>
                직접 추가하기
              </div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 2, lineHeight: '17px' }}>
                인식되지 않은 약 · 영양제 · 비처방약을 추가해요
              </div>
            </div>
            <Icon name="chevronR" size={18} stroke={2} />
          </div>

          {/* warning row */}
          <div style={{
            marginTop: 4, padding: 14, borderRadius: 12,
            background: 'var(--c-orange-95)', color: 'var(--c-orange-30)',
            display: 'flex', gap: 10, alignItems: 'flex-start',
          }}>
            <Icon name="info" size={18} stroke={2} />
            <div style={{ fontSize: 13, lineHeight: '19px', fontWeight: 500 }}>
              <b style={{ fontWeight: 700 }}>오메가-3</b>의 신뢰도가 낮아요 (78%). 직접 확인 후 등록을 추천해요.
            </div>
          </div>
        </div>

        {/* Manual add section — handles OCR misses */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 10, display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
            누락된 약 추가
          </div>
        </div>
        <div style={padX}>
          <div style={{ fontSize: 13, color: C.muted, lineHeight: '19px', marginBottom: 10 }}>
            처방전에 있지만 인식되지 않은 약이 있으면 직접 추가해주세요.
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {/* Search add */}
            <div style={{
              flex: 1, background: '#fff', border: `1px solid ${C.line}`, borderRadius: 14,
              padding: '14px 12px',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10,
                background: 'var(--c-blue-95)', color: 'var(--c-blue-45)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="search" size={20} stroke={2} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>검색으로 추가</div>
              <div style={{ fontSize: 11, color: C.alt, textAlign: 'center', lineHeight: '15px' }}>식약처 DB에서<br/>정확한 약 선택</div>
            </div>
            {/* Manual add */}
            <div style={{
              flex: 1, background: '#fff', border: `1px solid ${C.line}`, borderRadius: 14,
              padding: '14px 12px',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10,
                background: 'var(--c-violet-95)', color: 'var(--c-violet-45)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="pencil" size={20} stroke={2} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>직접 입력</div>
              <div style={{ fontSize: 11, color: C.alt, textAlign: 'center', lineHeight: '15px' }}>모양·색깔·복용 시간<br/>직접 설정</div>
            </div>
          </div>
        </div>

        {/* Memo section — notes about this prescription */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 10, display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
            메모
          </div>
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>선택 · 0/200</div>
        </div>
        <div style={padX}>
          <div style={{
            background: '#fff', borderRadius: 14, border: `1px solid ${C.line}`,
            padding: 14, minHeight: 96, position: 'relative',
          }}>
            <div style={{ fontSize: 14, color: C.assist, lineHeight: '20px' }}>
              이 처방전에 대한 메모를 남겨주세요.{'\n'}
              예: 식후 30분에 복용 · 어지러움 호소 시 의사 상담
            </div>
            <div style={{
              position: 'absolute', bottom: 10, right: 12,
              display: 'flex', gap: 4,
            }}>
              <div style={{
                padding: '4px 8px', borderRadius: 6,
                background: C.fill, color: C.muted,
                fontSize: 11, fontWeight: 600,
                display: 'flex', alignItems: 'center', gap: 3,
              }}>
                <Icon name="image" size={11} stroke={2} />
                사진
              </div>
              <div style={{
                padding: '4px 8px', borderRadius: 6,
                background: C.fill, color: C.muted,
                fontSize: 11, fontWeight: 600,
              }}>
                음성
              </div>
            </div>
          </div>
          {/* Quick chip prefills */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
            {['식후 30분', '공복 복용', '취침 30분 전', '의사 상담 필요'].map(p => (
              <div key={p} style={{
                padding: '6px 10px', borderRadius: 9999,
                background: '#fff', border: `1px solid ${C.line}`,
                fontSize: 12, color: C.muted, fontWeight: 500,
                display: 'flex', alignItems: 'center', gap: 4,
              }}>
                <Icon name="plus" size={11} stroke={2.4} />
                {p}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* CTA */}
      <div style={{
        ...padX, padding: '14px 20px 28px',
        background: '#fff', borderTop: `1px solid ${C.line}`,
      }}>
        <div style={{
          height: 54, borderRadius: 12, background: C.text, color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 16, fontWeight: 700, letterSpacing: '-0.012em',
        }}>5개 약 모두 등록하기</div>
        <div style={{ textAlign: 'center', marginTop: 8, fontSize: 12, color: C.alt }}>
          등록 후에도 추가·수정할 수 있어요
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenOnboarding, ScreenHome, ScreenScan, ScreenResult });
