// PillMate — Screens part 8: Prescription Registration entry hub

// ────────────────────────────────────────────────────────────────
// 16. Prescription Register — hub for camera / gallery / manual entry
// ────────────────────────────────────────────────────────────────
function ScreenRegister() {
  const recent = [
    { date: '11.10', title: '정형외과 처방', drugs: 2, pills: ['pink', 'violet'] },
    { date: '11.05', title: '안과 점안액', drugs: 1, pills: ['blue'] },
    { date: '10.18', title: '감기 증상 처방', drugs: 3, pills: ['pink', 'white', 'green'] },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="처방전 등록"
        right={<Icon name="search" size={22} stroke={1.8} />}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 24 }}>
        {/* Hero */}
        <div style={{ ...padX, paddingTop: 8 }}>
          <div style={{
            background: '#fff', borderRadius: 18, padding: '20px 18px',
            border: `1px solid ${C.line}`,
            display: 'flex', gap: 14, alignItems: 'center',
          }}>
            {/* prescription paper visual */}
            <div style={{
              width: 60, height: 76, borderRadius: 6,
              background: '#F4F1EA', position: 'relative', overflow: 'hidden',
              border: `1px solid ${C.line}`, flexShrink: 0,
              transform: 'rotate(-4deg)',
              boxShadow: '0 6px 14px rgba(0,0,0,0.08)',
            }}>
              {[8, 16, 24, 32, 40, 48, 56, 64].map(y => (
                <div key={y} style={{ position: 'absolute', left: 6, right: 6, top: y, height: 1.6, background: '#D0CABE' }} />
              ))}
              {/* AI sparkle badge */}
              <div style={{
                position: 'absolute', top: -8, right: -8,
                width: 22, height: 22, borderRadius: '50%',
                background: 'var(--c-violet-45)', color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name="sparkle" size={12} stroke={2} fill="currentColor" />
              </div>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 17, fontWeight: 700, color: C.text, letterSpacing: '-0.015em', lineHeight: '23px' }}>
                어떻게 등록할까요?
              </div>
              <div style={{ fontSize: 13, color: C.muted, marginTop: 4, lineHeight: '18px' }}>
                AI가 1.4초 만에 약을 인식해<br/>자동으로 등록해드려요
              </div>
            </div>
          </div>
        </div>

        {/* Primary action: Camera */}
        <div style={{ ...padX, paddingTop: 16 }}>
          <div style={{
            background: C.text, color: '#fff',
            borderRadius: 18, padding: 20,
            display: 'flex', alignItems: 'center', gap: 16,
            boxShadow: '0 10px 24px rgba(23,23,23,0.18)',
          }}>
            <div style={{
              width: 56, height: 56, borderRadius: 16,
              background: 'rgba(255,255,255,0.14)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon name="camera" size={28} stroke={2} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'rgba(255,255,255,0.7)' }}>
                  추천
                </div>
                <div style={{
                  padding: '2px 6px', borderRadius: 4,
                  background: 'rgba(255,255,255,0.18)', fontSize: 10, fontWeight: 700,
                  letterSpacing: '0.04em',
                }}>가장 빠름</div>
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, marginTop: 4, letterSpacing: '-0.015em' }}>
                카메라로 촬영하기
              </div>
              <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.7)', marginTop: 2, lineHeight: '17px' }}>
                처방전을 사각형 안에 맞추세요
              </div>
            </div>
            <Icon name="chevronR" size={22} stroke={2} />
          </div>
        </div>

        {/* Secondary actions: Gallery + Manual */}
        <div style={{ ...padX, paddingTop: 10, display: 'flex', gap: 10 }}>
          {/* Gallery */}
          <div style={{
            flex: 1, background: '#fff', borderRadius: 16,
            border: `1px solid ${C.line}`,
            padding: '18px 14px',
            display: 'flex', flexDirection: 'column', gap: 10,
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12,
              background: 'var(--c-blue-95)', color: 'var(--c-blue-45)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="image" size={22} stroke={2} />
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em' }}>
                갤러리에서
              </div>
              <div style={{ fontSize: 12, color: C.alt, marginTop: 2, lineHeight: '17px' }}>
                저장된 사진 선택
              </div>
            </div>
          </div>
          {/* Manual */}
          <div style={{
            flex: 1, background: '#fff', borderRadius: 16,
            border: `1px solid ${C.line}`,
            padding: '18px 14px',
            display: 'flex', flexDirection: 'column', gap: 10,
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12,
              background: 'var(--c-violet-95)', color: 'var(--c-violet-45)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="pencil" size={22} stroke={2} />
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em' }}>
                직접 입력
              </div>
              <div style={{ fontSize: 12, color: C.alt, marginTop: 2, lineHeight: '17px' }}>
                약 이름으로 등록
              </div>
            </div>
          </div>
        </div>

        {/* Tips card */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 10 }}>
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            촬영 팁
          </div>
        </div>
        <div style={padX}>
          <div style={{
            background: 'var(--c-blue-99)', borderRadius: 14, padding: 16,
            border: `1px solid var(--c-blue-95)`,
            display: 'flex', flexDirection: 'column', gap: 10,
          }}>
            {[
              ['처방전 전체가 사각형 안에 들어오게', true],
              ['그림자 없이 평평한 곳에 놓고 촬영', true],
              ['약 이름·용법이 잘 보이도록 충분히 가까이', true],
            ].map(([t, ok], i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                <div style={{
                  width: 20, height: 20, borderRadius: '50%',
                  background: 'var(--c-blue-45)', color: '#fff',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                }}>
                  <Icon name="check" size={12} stroke={3} />
                </div>
                <div style={{ flex: 1, fontSize: 13, color: 'var(--c-blue-30)', lineHeight: '19px', fontWeight: 500 }}>{t}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Recent prescriptions */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 10, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            최근 등록
          </div>
          <div style={{ fontSize: 13, color: C.primary, fontWeight: 600 }}>전체보기</div>
        </div>
        <div style={padX}>
          <div style={{ background: '#fff', borderRadius: 14, border: `1px solid ${C.line}`, overflow: 'hidden' }}>
            {recent.map((r, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '14px 16px',
                borderTop: i === 0 ? 'none' : `1px solid ${C.line}`,
              }}>
                <div style={{
                  width: 40, height: 50, borderRadius: 5,
                  background: '#F4F1EA', position: 'relative', overflow: 'hidden',
                  border: `1px solid ${C.line}`, flexShrink: 0,
                }}>
                  {[6, 12, 18, 24, 30, 36, 42].map(y => (
                    <div key={y} style={{ position: 'absolute', left: 4, right: 4, top: y, height: 1.3, background: '#D0CABE' }} />
                  ))}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>{r.title}</div>
                  <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>{r.date} · 약 {r.drugs}개</div>
                </div>
                <div style={{ display: 'flex' }}>
                  {r.pills.map((c, idx) => (
                    <div key={idx} style={{ marginLeft: idx === 0 ? 0 : -6 }}>
                      <PillVisual color={c} size={22} />
                    </div>
                  ))}
                </div>
                <Icon name="chevronR" size={18} stroke={2} />
              </div>
            ))}
          </div>
        </div>
      </div>

      <TabBar active="register" />
    </div>
  );
}

Object.assign(window, { ScreenRegister });
