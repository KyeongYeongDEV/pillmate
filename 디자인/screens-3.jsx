// PillMate — Screens part 3: Family Activity feed

// ────────────────────────────────────────────────────────────────
// 10. Family Activity — full feed of group events
// ────────────────────────────────────────────────────────────────
function ScreenActivity() {
  const [activeTab, setActiveTab] = useState('all');

  // Items per day with mixed event types
  const today = [
    {
      type: 'dose-done',
      who: '할머니', tint: '#FF7B2E', time: '12:34',
      title: '점심약 2개를 복용했어요',
      detail: ['메트포르민 500mg', '글리메피리드 2mg'],
      pills: ['orange', 'white'],
    },
    {
      type: 'insight',
      who: 'PillMate AI', tint: '#6541F2', time: '09:10',
      title: '저녁약을 자주 빠뜨려요',
      detail: '지난 7일 중 3일 미복용 — 알림 시간 조정을 추천해요',
      cta: '알림 조정',
    },
    {
      type: 'dose-done',
      who: '할머니', tint: '#FF7B2E', time: '08:12',
      title: '아침약 2개를 복용했어요',
      detail: ['암로디핀 5mg', '메트포르민 500mg'],
      pills: ['lightBlue', 'orange'],
    },
    {
      type: 'prescription',
      who: '엄마', tint: '#0066FF', time: '07:40',
      title: '새 처방전을 등록했어요',
      detail: '내과 진료 · 약 5개 추가',
      thumb: true,
    },
  ];

  const yesterday = [
    {
      type: 'dose-miss',
      who: '할머니', tint: '#FF7B2E', time: '22:00',
      title: '취침 전 약을 놓치셨어요',
      detail: ['오메가-3 1000mg'],
      pills: ['yellow'],
      miss: true,
    },
    {
      type: 'comment',
      who: '아들', tint: '#6541F2', time: '20:14',
      title: '메모를 남겼어요',
      detail: '"엄마, 오늘 어지러우셨다고 하셨어요. 다음 진료에서 여쭤봐요."',
    },
    {
      type: 'dose-done',
      who: '할머니', tint: '#FF7B2E', time: '19:08',
      title: '저녁약 1개를 복용했어요',
      detail: ['아토르바스타틴 10mg'],
      pills: ['pink'],
    },
  ];

  const earlier = [
    {
      type: 'member',
      who: '아들', tint: '#6541F2', time: '11.22',
      title: '케어 그룹에 참여했어요',
      detail: '초대 코드 사용 · 보호자 권한',
    },
    {
      type: 'report',
      who: 'PillMate AI', tint: '#6541F2', time: '11.21',
      title: '주간 리포트가 도착했어요',
      detail: '복약률 92% · 지난주보다 +5% 향상',
      cta: '리포트 열기',
    },
  ];

  const tabs = [
    { id: 'all', label: '전체' },
    { id: 'dose', label: '복약' },
    { id: 'rx', label: '처방전' },
    { id: 'ai', label: 'AI' },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="가족 활동"
        sub="할머니 댁 · 3명"
        right={<Icon name="filter" size={20} stroke={1.8} />}
      />

      {/* filter tabs */}
      <div style={{
        ...padX, paddingTop: 4, paddingBottom: 14,
        display: 'flex', gap: 6, background: C.bg,
        borderBottom: `1px solid ${C.line}`,
      }}>
        {tabs.map(t => {
          const on = t.id === activeTab;
          return (
            <div key={t.id}
              onClick={() => setActiveTab(t.id)}
              style={{
                padding: '8px 14px', borderRadius: 9999,
                background: on ? C.text : C.fill,
                color: on ? '#fff' : C.muted,
                fontSize: 13, fontWeight: on ? 700 : 500,
                letterSpacing: '-0.005em',
              }}>
              {t.label}
            </div>
          );
        })}
      </div>

      {/* feed */}
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 24 }}>
        <DaySection title="오늘 · 11월 24일 월" items={today} first />
        <DaySection title="어제 · 11월 23일 일" items={yesterday} />
        <DaySection title="이전 활동" items={earlier} />

        <div style={{ padding: '32px 20px 16px', textAlign: 'center', fontSize: 12, color: C.assist }}>
          최근 30일 활동만 표시됩니다
        </div>
      </div>
    </div>
  );
}

// Section grouping per day, with a vertical timeline rail
function DaySection({ title, items, first }) {
  return (
    <div style={{ paddingTop: first ? 16 : 24 }}>
      <div style={{ ...padX, paddingBottom: 12, fontSize: 12, color: C.alt, fontWeight: 700, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
        {title}
      </div>
      <div style={{ paddingLeft: 20, paddingRight: 20 }}>
        {items.map((it, i) => (
          <ActivityItem key={i} item={it} last={i === items.length - 1} />
        ))}
      </div>
    </div>
  );
}

// One activity row with timeline rail on the left
function ActivityItem({ item, last }) {
  const m = item;
  // dot color by type
  const dotColor =
    m.type === 'dose-miss' ? C.negative :
    m.type === 'dose-done' ? C.positive :
    m.type === 'insight'   ? 'var(--c-violet-45)' :
    m.type === 'report'    ? 'var(--c-violet-45)' :
    m.type === 'prescription' ? C.primary :
    m.type === 'comment'   ? 'var(--c-cyan-50)' :
    m.type === 'member'    ? 'var(--c-pink-46)' :
    C.muted;

  return (
    <div style={{ display: 'flex', gap: 14, alignItems: 'stretch' }}>
      {/* rail */}
      <div style={{ width: 14, position: 'relative', flexShrink: 0 }}>
        <div style={{
          position: 'absolute', left: '50%', top: 22, bottom: last ? '50%' : -2,
          width: 2, background: C.line, transform: 'translateX(-1px)',
        }} />
        <div style={{
          position: 'absolute', left: '50%', top: 18, width: 10, height: 10,
          borderRadius: '50%', background: dotColor,
          transform: 'translate(-50%, 0)',
          boxShadow: `0 0 0 3px ${C.bgAlt}`,
        }} />
      </div>

      {/* card */}
      <div style={{ flex: 1, paddingBottom: last ? 0 : 12, minWidth: 0 }}>
        <div style={{
          background: '#fff', borderRadius: 14, padding: '14px 16px',
          border: `1px solid ${C.line}`,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar name={m.who[0]} tint={m.tint} size={28} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, color: C.text }}>
                <span style={{ fontWeight: 700 }}>{m.who}</span>
              </div>
            </div>
            <div style={{ fontSize: 12, color: C.alt, fontWeight: 500 }}>{m.time}</div>
          </div>

          {/* title */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 10 }}>
            {m.miss && (
              <div style={{ width: 16, height: 16, borderRadius: 4, background: 'var(--c-red-95)', color: 'var(--c-red-40)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="warn" size={11} stroke={2.4} />
              </div>
            )}
            <div style={{
              fontSize: 15, fontWeight: 700, color: C.text,
              letterSpacing: '-0.01em', lineHeight: '21px',
            }}>
              {m.title}
            </div>
          </div>

          {/* detail */}
          {Array.isArray(m.detail) ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 10, padding: '10px 12px', borderRadius: 10, background: C.bgAlt }}>
              {m.pills && (
                <div style={{ display: 'flex' }}>
                  {m.pills.map((p, idx) => (
                    <div key={idx} style={{ marginLeft: idx === 0 ? 0 : -8 }}>
                      <PillVisual color={p} size={26} />
                    </div>
                  ))}
                </div>
              )}
              <div style={{ flex: 1, fontSize: 13, color: C.muted, lineHeight: '19px' }}>
                {m.detail.join(' · ')}
              </div>
            </div>
          ) : (
            <div style={{ fontSize: 13, color: C.muted, marginTop: 6, lineHeight: '19px' }}>
              {m.detail}
            </div>
          )}

          {/* prescription thumb (special) */}
          {m.thumb && (
            <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 10, padding: 10, borderRadius: 10, background: C.bgAlt }}>
              <div style={{
                width: 36, height: 44, borderRadius: 6, background: '#F4F1EA',
                position: 'relative', overflow: 'hidden', flexShrink: 0,
                border: `1px solid ${C.line}`,
              }}>
                {[6, 12, 18, 24, 30].map(y => (
                  <div key={y} style={{ position: 'absolute', left: 5, right: 5, top: y, height: 1.5, background: '#D0CABE' }} />
                ))}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>2025-11-24 처방전</div>
                <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>약 5개 · 21일분</div>
              </div>
              <Icon name="chevronR" size={18} stroke={2} />
            </div>
          )}

          {/* CTA */}
          {m.cta && (
            <div style={{ marginTop: 12, display: 'flex', gap: 6 }}>
              <div style={{ padding: '7px 12px', borderRadius: 8, background: C.text, color: '#fff', fontSize: 13, fontWeight: 600 }}>
                {m.cta}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenActivity });
