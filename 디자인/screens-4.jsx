// PillMate — Screens part 4: Drug Search

// ────────────────────────────────────────────────────────────────
// 11. Drug Search — search 식약처 DB
// ────────────────────────────────────────────────────────────────
function ScreenSearch() {
  const results = [
    {
      name: '암로디핀정 5mg',
      en: 'Amlodipine Besylate 5mg',
      maker: '한미약품',
      tags: ['혈압강하제', 'ARB'],
      color: 'lightBlue',
      taken: true, // 우리 가족이 복용 중
    },
    {
      name: '암로디핀베실산염정 5mg',
      en: 'Amlodipine Besylate 5mg',
      maker: '대웅제약',
      tags: ['혈압강하제'],
      color: 'lightBlue',
    },
    {
      name: '암로핀정 10mg',
      en: 'Amlodipine 10mg',
      maker: '종근당',
      tags: ['혈압강하제', 'CCB'],
      color: 'blue',
    },
    {
      name: '노바스크정 5mg',
      en: 'Norvasc 5mg',
      maker: '한국화이자',
      tags: ['혈압강하제', '오리지널'],
      color: 'white',
    },
    {
      name: '카나브정 30mg',
      en: 'Kanarb 30mg',
      maker: '보령제약',
      tags: ['혈압강하제', 'ARB'],
      color: 'pink',
    },
  ];

  const recent = ['메트포르민', '오메가-3', '글리메피리드'];
  const categories = [
    { name: '고혈압', icon: '心', tint: 'var(--c-red-95)', fg: 'var(--c-red-40)' },
    { name: '당뇨', icon: '糖', tint: 'var(--c-orange-95)', fg: 'var(--c-orange-40)' },
    { name: '콜레스테롤', icon: '脂', tint: 'var(--c-violet-95)', fg: 'var(--c-violet-45)' },
    { name: '소화제', icon: '胃', tint: 'var(--c-green-95)', fg: 'var(--c-green-40)' },
    { name: '진통제', icon: '痛', tint: 'var(--c-blue-95)', fg: 'var(--c-blue-45)' },
    { name: '감기', icon: '冒', tint: 'var(--c-cyan-95)', fg: 'var(--c-cyan-40)' },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bg }}>
      {/* header with cancel */}
      <div style={{ ...padX, paddingTop: 8, paddingBottom: 14, display: 'flex', alignItems: 'center', gap: 12, background: C.bg }}>
        <div style={{
          flex: 1, height: 44, borderRadius: 12,
          background: C.fill, border: `1.5px solid ${C.primary}`,
          display: 'flex', alignItems: 'center', gap: 10, padding: '0 14px',
        }}>
          <Icon name="search" size={20} stroke={2} />
          <div style={{ flex: 1, fontSize: 15, color: C.text, fontWeight: 500, letterSpacing: '-0.005em' }}>
            암로디핀
            <span style={{ display: 'inline-block', width: 1.5, height: 16, background: C.primary, marginLeft: 2, verticalAlign: 'middle', animation: 'pmCursor 1s infinite' }} />
          </div>
          <div style={{ width: 18, height: 18, borderRadius: '50%', background: 'var(--label-assistive)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="close" size={12} stroke={2.6} />
          </div>
        </div>
        <div style={{ fontSize: 15, color: C.text, fontWeight: 600 }}>취소</div>
      </div>

      {/* AI search toggle row */}
      <div style={{ ...padX, paddingBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '7px 12px', borderRadius: 9999,
          background: 'var(--c-violet-95)', color: 'var(--c-violet-45)',
          fontSize: 12, fontWeight: 700,
        }}>
          <Icon name="sparkle" size={14} stroke={2} fill="currentColor" />
          AI 의미 검색
        </div>
        <div style={{
          padding: '7px 12px', borderRadius: 9999,
          background: C.fill, color: C.muted,
          fontSize: 12, fontWeight: 600,
        }}>이름</div>
        <div style={{
          padding: '7px 12px', borderRadius: 9999,
          background: C.fill, color: C.muted,
          fontSize: 12, fontWeight: 600,
        }}>성분</div>
        <div style={{
          padding: '7px 12px', borderRadius: 9999,
          background: C.fill, color: C.muted,
          fontSize: 12, fontWeight: 600,
        }}>효능</div>
      </div>

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 16 }}>
        {/* result count */}
        <div style={{ ...padX, paddingTop: 12, paddingBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, color: C.muted }}>
            검색 결과 <b style={{ color: C.text }}>{results.length}건</b>
          </div>
          <div style={{ fontSize: 13, color: C.alt, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
            관련도순 <Icon name="chevronD" size={14} stroke={2.2} />
          </div>
        </div>

        {/* result cards */}
        <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 8 }}>
          {results.map((d, i) => (
            <div key={i} style={{
              background: '#fff', borderRadius: 14, padding: 14,
              border: `1px solid ${C.line}`,
              display: 'flex', gap: 12, alignItems: 'center',
            }}>
              <PillVisual color={d.color} size={40} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em' }}>
                    <Highlight text={d.name} term="암로" />
                  </div>
                  {d.taken && (
                    <div style={{ fontSize: 10, fontWeight: 700, padding: '2px 6px', borderRadius: 4, background: 'var(--c-blue-95)', color: 'var(--c-blue-45)', letterSpacing: '0.02em' }}>
                      복용 중
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>{d.en} · {d.maker}</div>
                <div style={{ display: 'flex', gap: 4, marginTop: 8 }}>
                  {d.tags.map(t => (
                    <span key={t} style={{ fontSize: 11, padding: '2px 7px', borderRadius: 4, background: C.fill, color: C.muted, fontWeight: 600 }}>{t}</span>
                  ))}
                </div>
              </div>
              <div style={{ width: 32, height: 32, borderRadius: 8, background: C.fill, color: C.text, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="plus" size={18} stroke={2.2} />
              </div>
            </div>
          ))}
        </div>

        {/* divider */}
        <div style={{ height: 8, background: C.bgAlt, marginTop: 20 }} />

        {/* recent searches */}
        <div style={{ ...padX, paddingTop: 20, paddingBottom: 10, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>최근 검색</div>
          <div style={{ fontSize: 12, color: C.alt }}>전체 삭제</div>
        </div>
        <div style={{ ...padX, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {recent.map(r => (
            <div key={r} style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '8px 12px', borderRadius: 9999,
              background: C.bg, border: `1px solid ${C.line}`,
              fontSize: 13, color: C.text, fontWeight: 500,
            }}>
              <Icon name="clock" size={13} stroke={2} />
              {r}
              <div style={{ width: 14, height: 14, borderRadius: '50%', background: 'var(--label-assistive)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', marginLeft: 2 }}>
                <Icon name="close" size={9} stroke={2.6} />
              </div>
            </div>
          ))}
        </div>

        {/* categories */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>카테고리로 찾기</div>
        </div>
        <div style={{ ...padX, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
          {categories.map(cat => (
            <div key={cat.name} style={{
              background: '#fff', border: `1px solid ${C.line}`,
              borderRadius: 14, padding: '16px 12px',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
            }}>
              <div style={{
                width: 40, height: 40, borderRadius: 10,
                background: cat.tint, color: cat.fg,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 18, fontWeight: 700,
                fontFamily: 'var(--font-display)',
              }}>{cat.icon}</div>
              <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>{cat.name}</div>
            </div>
          ))}
        </div>

        {/* AI hint */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 8 }}>
          <div style={{
            background: 'var(--c-violet-95)', borderRadius: 14, padding: 16,
            display: 'flex', alignItems: 'flex-start', gap: 12,
          }}>
            <div style={{
              width: 32, height: 32, borderRadius: 10,
              background: '#fff', color: 'var(--c-violet-45)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon name="sparkle" size={18} stroke={2} fill="currentColor" />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--c-violet-30)', letterSpacing: '-0.005em' }}>약 이름이 기억나지 않으세요?</div>
              <div style={{ fontSize: 12, color: 'var(--c-violet-30)', marginTop: 4, lineHeight: '18px', opacity: 0.85 }}>
                "흰색 동그란 알약, 혈압약" 처럼 설명해도 찾아드려요.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* keyboard area hint (no real keyboard) */}
      <div style={{
        height: 56, background: '#fff',
        borderTop: `1px solid ${C.line}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 12, color: C.assist,
      }}>
        키보드 활성 상태
      </div>

      <style>{`@keyframes pmCursor { 0%,49%{opacity:1} 50%,100%{opacity:0} }`}</style>
    </div>
  );
}

// inline highlight helper
function Highlight({ text, term }) {
  if (!term) return text;
  const idx = text.indexOf(term);
  if (idx < 0) return text;
  return (
    <>
      {text.slice(0, idx)}
      <span style={{ color: C.primary }}>{text.slice(idx, idx + term.length)}</span>
      {text.slice(idx + term.length)}
    </>
  );
}

Object.assign(window, { ScreenSearch });
