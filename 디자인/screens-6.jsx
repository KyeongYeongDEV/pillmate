// PillMate — Screens part 6: Notifications center

// ────────────────────────────────────────────────────────────────
// 13. Notifications — global notification inbox
// ────────────────────────────────────────────────────────────────
function ScreenNotifications() {
  const tabs = [
    { id: 'all', label: '전체', n: 12 },
    { id: 'unread', label: '안 읽음', n: 4 },
    { id: 'dose', label: '복약' },
    { id: 'family', label: '가족' },
    { id: 'ai', label: 'AI' },
  ];

  const today = [
    {
      kind: 'dose-reminder',
      title: '점심약 복용 시간이에요',
      body: '메트포르민 500mg · 글리메피리드 2mg',
      time: '방금 전',
      unread: true,
      pills: ['orange', 'white'],
      cta: ['복용 완료', '15분 후'],
    },
    {
      kind: 'family-done',
      title: '할머니가 아침약을 복용했어요',
      body: '암로디핀 5mg · 메트포르민 500mg · 08:12',
      time: '4시간 전',
      who: '할', tint: '#FF7B2E',
      unread: true,
    },
    {
      kind: 'ai',
      title: 'AI 인사이트가 도착했어요',
      body: '저녁약을 자주 빠뜨리고 계세요. 알림 시간을 조정해볼까요?',
      time: '오전 9:10',
      unread: true,
    },
  ];

  const yesterday = [
    {
      kind: 'dose-miss',
      title: '취침 전 약을 놓치셨어요',
      body: '오메가-3 1000mg · 미복용 12시간',
      time: '어제 22:30',
      pills: ['yellow'],
    },
    {
      kind: 'family-rx',
      title: '엄마가 새 처방전을 등록했어요',
      body: '약 5개 추가 · 21일분',
      time: '어제 19:14',
      who: '엄', tint: '#0066FF',
    },
    {
      kind: 'family-comment',
      title: '아들이 메모를 남겼어요',
      body: '"엄마, 다음 진료에서 어지러움 여쭤봐요."',
      time: '어제 20:14',
      who: '아', tint: '#6541F2',
      unread: true,
    },
    {
      kind: 'dose-done',
      title: '저녁약 복용 완료',
      body: '아토르바스타틴 10mg · 19:08',
      time: '어제 19:08',
      pills: ['pink'],
    },
  ];

  const earlier = [
    {
      kind: 'report',
      title: '주간 복약 리포트가 도착했어요',
      body: '복약률 92% · 지난주보다 +5% 향상',
      time: '11.22',
    },
    {
      kind: 'system',
      title: '암로디핀 처방이 5일 남았어요',
      body: '재처방을 미리 받아보세요',
      time: '11.21',
    },
    {
      kind: 'family-join',
      title: '아들이 케어 그룹에 참여했어요',
      body: '보호자 권한',
      time: '11.20',
      who: '아', tint: '#6541F2',
    },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="알림"
        sub="안 읽은 알림 4건"
        right={<div style={{ fontSize: 13, color: C.primary, fontWeight: 600 }}>모두 읽음</div>}
      />

      {/* filter chips */}
      <div style={{
        ...padX, paddingTop: 4, paddingBottom: 12,
        display: 'flex', gap: 6, background: C.bg,
        borderBottom: `1px solid ${C.line}`,
        overflowX: 'auto',
      }}>
        {tabs.map((t, i) => {
          const on = i === 0;
          return (
            <div key={t.id} style={{
              padding: '8px 14px', borderRadius: 9999,
              background: on ? C.text : C.fill,
              color: on ? '#fff' : C.muted,
              fontSize: 13, fontWeight: on ? 700 : 500,
              letterSpacing: '-0.005em',
              display: 'flex', alignItems: 'center', gap: 6,
              flexShrink: 0,
            }}>
              {t.label}
              {t.n != null && (
                <span style={{
                  fontSize: 11, padding: '1px 6px', borderRadius: 9999,
                  background: on ? 'rgba(255,255,255,0.22)' : '#fff',
                  color: on ? '#fff' : C.alt, fontWeight: 700,
                }}>{t.n}</span>
              )}
            </div>
          );
        })}
      </div>

      {/* feed */}
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 24 }}>
        <NotifGroup title="오늘 · 11월 24일 월" items={today} first />
        <NotifGroup title="어제" items={yesterday} />
        <NotifGroup title="이전" items={earlier} />

        {/* settings entry */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 8 }}>
          <div style={{
            background: '#fff', borderRadius: 14, padding: 14,
            border: `1px solid ${C.line}`,
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: C.fill, color: C.text,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="settings" size={20} stroke={1.8} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>알림 설정</div>
              <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>받을 알림 종류 · 방해금지 시간</div>
            </div>
            <Icon name="chevronR" size={18} stroke={2} />
          </div>
        </div>

        <div style={{ padding: '20px 20px 12px', textAlign: 'center', fontSize: 12, color: C.assist }}>
          최근 30일 알림만 표시됩니다
        </div>
      </div>
    </div>
  );
}

// One day section
function NotifGroup({ title, items, first }) {
  return (
    <div style={{ paddingTop: first ? 14 : 20 }}>
      <div style={{ ...padX, paddingBottom: 8, fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
        {title}
      </div>
      <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items.map((it, i) => <NotifItem key={i} item={it} />)}
      </div>
    </div>
  );
}

// One notification card
function NotifItem({ item }) {
  const m = item;

  // icon block per kind
  const iconMap = {
    'dose-reminder': { icon: 'bell',   tint: 'var(--c-blue-95)',   fg: 'var(--c-blue-45)',   label: '복약 알림' },
    'dose-done':     { icon: 'check',  tint: 'var(--c-green-95)',  fg: 'var(--c-green-40)',  label: '복약 완료' },
    'dose-miss':     { icon: 'warn',   tint: 'var(--c-red-95)',    fg: 'var(--c-red-40)',    label: '미복용' },
    'family-done':   { avatar: true,                                                       label: '가족 활동' },
    'family-rx':     { avatar: true,                                                       label: '처방전' },
    'family-comment':{ avatar: true,                                                       label: '메모' },
    'family-join':   { avatar: true,                                                       label: '그룹' },
    'ai':            { icon: 'sparkle', tint: 'var(--c-violet-95)', fg: 'var(--c-violet-45)', label: 'AI 인사이트', fill: true },
    'report':        { icon: 'chart',   tint: 'var(--c-violet-95)', fg: 'var(--c-violet-45)', label: '리포트' },
    'system':        { icon: 'info',    tint: C.fill,               fg: C.muted,              label: '시스템' },
  };
  const meta = iconMap[m.kind] || iconMap.system;

  return (
    <div style={{
      background: '#fff', borderRadius: 14, padding: 14,
      border: `1px solid ${C.line}`,
      display: 'flex', gap: 12, alignItems: 'flex-start',
      position: 'relative',
    }}>
      {/* unread dot */}
      {m.unread && (
        <div style={{
          position: 'absolute', top: 16, right: 14,
          width: 8, height: 8, borderRadius: '50%', background: C.primary,
        }} />
      )}

      {/* icon */}
      {meta.avatar ? (
        <Avatar name={m.who} tint={m.tint} size={36} />
      ) : (
        <div style={{
          width: 36, height: 36, borderRadius: 10,
          background: meta.tint, color: meta.fg,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0,
        }}>
          <Icon name={meta.icon} size={18} stroke={2} fill={meta.fill ? 'currentColor' : 'none'} />
        </div>
      )}

      {/* content */}
      <div style={{ flex: 1, minWidth: 0, paddingRight: m.unread ? 12 : 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: meta.fg || C.alt, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
            {meta.label}
          </div>
          <div style={{ width: 2, height: 2, borderRadius: '50%', background: C.assist }} />
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>{m.time}</div>
        </div>
        <div style={{
          fontSize: 14, fontWeight: 700, marginTop: 4, color: C.text,
          letterSpacing: '-0.01em', lineHeight: '20px',
        }}>{m.title}</div>
        <div style={{ fontSize: 13, color: C.muted, marginTop: 3, lineHeight: '19px' }}>
          {m.body}
        </div>

        {/* pill row (if med-related) */}
        {m.pills && (
          <div style={{ display: 'flex', gap: 0, marginTop: 10 }}>
            {m.pills.map((p, idx) => (
              <div key={idx} style={{ marginLeft: idx === 0 ? 0 : -8 }}>
                <PillVisual color={p} size={26} />
              </div>
            ))}
          </div>
        )}

        {/* CTAs */}
        {m.cta && (
          <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
            <div style={{
              padding: '7px 13px', borderRadius: 8,
              background: C.text, color: '#fff',
              fontSize: 13, fontWeight: 600,
            }}>{m.cta[0]}</div>
            <div style={{
              padding: '7px 13px', borderRadius: 8,
              background: C.fill, color: C.muted,
              fontSize: 13, fontWeight: 600,
            }}>{m.cta[1]}</div>
          </div>
        )}
      </div>
    </div>
  );
}

Object.assign(window, { ScreenNotifications });
