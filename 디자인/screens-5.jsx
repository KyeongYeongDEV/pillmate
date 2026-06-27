// PillMate — Screens part 5: Manual Drug Entry form

// ────────────────────────────────────────────────────────────────
// 12. Manual Add — direct entry form for a single medication
// ────────────────────────────────────────────────────────────────
function ScreenManualAdd() {
  // selected toggles (visual only)
  const shape = '타원형';
  const color = 'lightBlue';
  const slots = { 아침: true, 점심: false, 저녁: true, 취침전: false };

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="close" size={24} stroke={2} />}
        title="약 직접 추가"
        sub="처방전에 없는 약도 등록할 수 있어요"
        right={<div style={{ fontSize: 14, color: C.alt, fontWeight: 500 }}>초기화</div>}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 100 }}>
        {/* Pill preview hero */}
        <div style={padX}>
          <div style={{
            background: '#fff', borderRadius: 16, padding: '20px 18px',
            border: `1px solid ${C.line}`,
            display: 'flex', gap: 16, alignItems: 'center',
          }}>
            <div style={{
              width: 72, height: 72, borderRadius: 14,
              background: C.bgAlt,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <PillVisual color={color} size={52} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, letterSpacing: '0.04em' }}>미리보기</div>
              <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4, color: C.text, letterSpacing: '-0.012em' }}>
                새 약
              </div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 2 }}>
                정보를 입력해주세요
              </div>
            </div>
          </div>
        </div>

        {/* ── Section 1: 약 이름 ── */}
        <FormSection title="약 이름" required>
          <div style={{
            background: '#fff', borderRadius: 12,
            border: `1.5px solid ${C.primary}`,
            padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10,
            height: 52,
          }}>
            <div style={{ flex: 1, fontSize: 16, color: C.text, fontWeight: 500, letterSpacing: '-0.005em' }}>
              영양제 비타민D
              <span style={{ display: 'inline-block', width: 1.5, height: 18, background: C.primary, marginLeft: 2, verticalAlign: 'middle', animation: 'pmCursor 1s infinite' }} />
            </div>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 4,
              padding: '6px 10px', borderRadius: 8,
              background: 'var(--c-blue-95)', color: 'var(--c-blue-45)',
              fontSize: 12, fontWeight: 700,
            }}>
              <Icon name="search" size={14} stroke={2.2} />
              DB 검색
            </div>
          </div>
          <div style={{ fontSize: 12, color: C.alt, marginTop: 8, paddingLeft: 4 }}>
            식약처 DB에서 찾으면 자동으로 정보가 채워져요
          </div>
        </FormSection>

        {/* ── Section 2: 모양 · 색깔 ── */}
        <FormSection title="모양과 색깔">
          <div style={{ fontSize: 12, color: C.alt, marginBottom: 10, fontWeight: 600 }}>모양</div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
            {[
              { name: '원형', shape: 'circle' },
              { name: '타원형', shape: 'oval' },
              { name: '캡슐', shape: 'capsule' },
              { name: '기타', shape: 'other' },
            ].map(s => {
              const on = s.name === shape;
              return (
                <div key={s.name} style={{
                  flex: 1, height: 64, borderRadius: 12,
                  background: '#fff',
                  border: on ? `1.5px solid ${C.primary}` : `1px solid ${C.line}`,
                  display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
                  color: on ? C.text : C.muted,
                }}>
                  <ShapeIcon shape={s.shape} active={on} />
                  <div style={{ fontSize: 11, fontWeight: on ? 700 : 500 }}>{s.name}</div>
                </div>
              );
            })}
          </div>

          <div style={{ fontSize: 12, color: C.alt, marginBottom: 10, fontWeight: 600 }}>색깔</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {[
              ['lightBlue', '#A1E1FF', '하늘'],
              ['orange',    '#FFC06E', '주황'],
              ['pink',      '#FFB8F3', '분홍'],
              ['violet',    '#C0B0FF', '보라'],
              ['green',     '#ACFCC7', '연두'],
              ['yellow',    '#FFE074', '노랑'],
              ['white',     '#FFFFFF', '흰색'],
              ['blue',      '#9EC5FF', '파랑'],
            ].map(([id, hex, label]) => {
              const on = id === color;
              return (
                <div key={id} style={{
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                }}>
                  <div style={{
                    width: 38, height: 38, borderRadius: '50%',
                    background: hex,
                    border: on ? `2px solid ${C.text}` : `1px solid ${C.line}`,
                    boxShadow: on ? '0 0 0 2px #fff inset' : 'none',
                  }} />
                  <div style={{ fontSize: 10, color: on ? C.text : C.alt, fontWeight: on ? 700 : 500 }}>{label}</div>
                </div>
              );
            })}
          </div>
        </FormSection>

        {/* ── Section 3: 용량 ── */}
        <FormSection title="1회 복용량" required>
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{
              flex: 1, height: 52, background: '#fff', borderRadius: 12,
              border: `1px solid ${C.line}`,
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '0 14px',
            }}>
              <div style={{ fontSize: 16, color: C.text, fontWeight: 600 }}>1</div>
              <div style={{ display: 'flex', gap: 4 }}>
                <div style={{ width: 28, height: 28, borderRadius: 6, background: C.fill, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted }}>−</div>
                <div style={{ width: 28, height: 28, borderRadius: 6, background: C.fill, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted }}>+</div>
              </div>
            </div>
            <div style={{
              minWidth: 110, height: 52, background: '#fff', borderRadius: 12,
              border: `1px solid ${C.line}`,
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '0 14px',
              fontSize: 15, color: C.text, fontWeight: 600,
            }}>
              정
              <Icon name="chevronD" size={16} stroke={2.2} />
            </div>
          </div>
        </FormSection>

        {/* ── Section 4: 복용 시간대 ── */}
        <FormSection title="복용 시간대" required>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
            {[
              ['아침', '08:00', '🌅'],
              ['점심', '12:30', '🌞'],
              ['저녁', '19:00', '🌇'],
              ['취침전', '22:00', '🌙'],
            ].map(([k, time]) => {
              const on = slots[k];
              return (
                <div key={k} style={{
                  height: 72, borderRadius: 12,
                  background: on ? C.text : '#fff',
                  border: on ? 'none' : `1px solid ${C.line}`,
                  color: on ? '#fff' : C.muted,
                  display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
                }}>
                  <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: '-0.005em' }}>{k}</div>
                  <div style={{ fontSize: 11, opacity: on ? 0.8 : 1, color: on ? 'rgba(255,255,255,0.7)' : C.alt }}>{time}</div>
                </div>
              );
            })}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 12, padding: '8px 12px', borderRadius: 8, background: C.fill }}>
            <Icon name="clock" size={14} stroke={2} />
            <div style={{ fontSize: 12, color: C.muted, fontWeight: 500 }}>
              하루 <b style={{ color: C.text }}>2회</b> 복용 · 아침·저녁
            </div>
          </div>
        </FormSection>

        {/* ── Section 5: 복용 기간 ── */}
        <FormSection title="복용 기간">
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <DateField label="시작일" value="2025.11.24" />
            <div style={{ color: C.alt, padding: '0 4px' }}>→</div>
            <DateField label="종료일" value="장기" muted />
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
            {['7일', '14일', '30일', '90일', '장기'].map((p, i) => (
              <div key={p} style={{
                padding: '6px 11px', borderRadius: 9999,
                background: i === 4 ? C.text : '#fff',
                color: i === 4 ? '#fff' : C.muted,
                border: i === 4 ? 'none' : `1px solid ${C.line}`,
                fontSize: 12, fontWeight: i === 4 ? 700 : 500,
              }}>{p}</div>
            ))}
          </div>
        </FormSection>

        {/* ── Section 6: 메모 ── */}
        <FormSection title="메모" sub="선택">
          <div style={{
            background: '#fff', borderRadius: 12, border: `1px solid ${C.line}`,
            padding: 14, minHeight: 76, fontSize: 14, color: C.assist, lineHeight: '21px',
          }}>
            예: 식후 30분에 복용, 우유와 함께 드시지 마세요
          </div>
        </FormSection>

        {/* Tip */}
        <div style={{ ...padX, paddingTop: 8 }}>
          <div style={{
            background: 'var(--c-violet-95)', borderRadius: 12, padding: 14,
            display: 'flex', gap: 10, alignItems: 'flex-start',
          }}>
            <Icon name="sparkle" size={18} stroke={2} fill="var(--c-violet-45)" />
            <div style={{ fontSize: 12, color: 'var(--c-violet-30)', lineHeight: '18px' }}>
              <b style={{ fontWeight: 700 }}>AI 검증</b> · 저장 시 입력한 약이 식약처 DB와 매칭되는지 확인하고, 병용금기가 있으면 알려드려요.
            </div>
          </div>
        </div>
      </div>

      {/* CTA */}
      <div style={{
        ...padX, padding: '14px 20px 28px',
        background: '#fff', borderTop: `1px solid ${C.line}`,
        display: 'flex', gap: 8,
      }}>
        <div style={{
          flex: '0 0 auto', minWidth: 88, height: 54, borderRadius: 12,
          background: C.fill, color: C.muted,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 15, fontWeight: 600, padding: '0 16px',
        }}>취소</div>
        <div style={{
          flex: 1, height: 54, borderRadius: 12, background: C.text, color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 16, fontWeight: 700, letterSpacing: '-0.012em',
        }}>약 추가</div>
      </div>

      <style>{`@keyframes pmCursor { 0%,49%{opacity:1} 50%,100%{opacity:0} }`}</style>
    </div>
  );
}

// Field section wrapper
function FormSection({ title, sub, required, children }) {
  return (
    <div style={{ ...padX, paddingTop: 24 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 10 }}>
        <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>{title}</div>
        {required && <div style={{ width: 5, height: 5, borderRadius: '50%', background: C.negative }} />}
        {sub && <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>{sub}</div>}
      </div>
      {children}
    </div>
  );
}

// Date field
function DateField({ label, value, muted }) {
  return (
    <div style={{
      flex: 1, height: 52, background: '#fff', borderRadius: 12,
      border: `1px solid ${C.line}`,
      padding: '8px 14px',
      display: 'flex', flexDirection: 'column', justifyContent: 'center',
    }}>
      <div style={{ fontSize: 10, color: C.alt, fontWeight: 600, letterSpacing: '0.04em' }}>{label}</div>
      <div style={{ fontSize: 14, color: muted ? C.alt : C.text, fontWeight: 600, marginTop: 1 }}>{value}</div>
    </div>
  );
}

// Pill shape icon
function ShapeIcon({ shape, active }) {
  const c = active ? C.text : 'var(--label-assistive)';
  if (shape === 'circle')   return <div style={{ width: 22, height: 22, borderRadius: '50%', background: c }} />;
  if (shape === 'oval')     return <div style={{ width: 28, height: 18, borderRadius: 9, background: c }} />;
  if (shape === 'capsule')  return (
    <div style={{ width: 30, height: 14, borderRadius: 7, overflow: 'hidden', display: 'flex' }}>
      <div style={{ flex: 1, background: c }} />
      <div style={{ flex: 1, background: c, opacity: 0.4 }} />
    </div>
  );
  return (
    <div style={{ fontSize: 14, fontWeight: 700, color: c, marginTop: -2 }}>…</div>
  );
}

Object.assign(window, { ScreenManualAdd });
