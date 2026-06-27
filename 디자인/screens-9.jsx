// PillMate — Screens part 9: Groups list (multi-group inbox)

// ────────────────────────────────────────────────────────────────
// 17. Groups List — multiple care groups, chat-list style
// ────────────────────────────────────────────────────────────────
function ScreenGroups() {
  const groups = [
    {
      id: 'grandma',
      name: '할머니 댁',
      desc: '엄마·아들·딸 보호 · 박순자님',
      memberCount: 4,
      avatars: [
        { name: '박', tint: '#FF7B2E' },
        { name: '민', tint: '#0066FF' },
        { name: '아', tint: '#6541F2' },
      ],
      lastEvent: { who: '할머니', text: '아침약 2개 복용 완료', time: '08:12', kind: 'done' },
      unread: 2,
      pinned: true,
    },
    {
      id: 'dad',
      name: '아빠 건강관리',
      desc: '엄마·딸 · 김영수님',
      memberCount: 3,
      avatars: [
        { name: '김', tint: '#26C76C' },
        { name: '민', tint: '#0066FF' },
        { name: '엄', tint: '#FA73E3' },
      ],
      lastEvent: { who: 'PillMate AI', text: '주간 리포트 도착', time: '어제', kind: 'ai' },
      unread: 0,
    },
    {
      id: 'me',
      name: '내 복약 일지',
      desc: '본인만 · 비공개',
      memberCount: 1,
      avatars: [
        { name: '민', tint: '#0066FF' },
      ],
      lastEvent: { who: '나', text: '비타민D 복용 완료', time: '어제 22:30', kind: 'done' },
      unread: 0,
      personal: true,
    },
    {
      id: 'inlaws',
      name: '시댁 어르신',
      desc: '시아버지·시어머니 · 5명',
      memberCount: 5,
      avatars: [
        { name: '이', tint: '#D7B33A' },
        { name: '박', tint: '#FF7B2E' },
        { name: '윤', tint: '#6541F2' },
      ],
      lastEvent: { who: '시아버지', text: '저녁약을 놓치셨어요', time: '어제 22:30', kind: 'miss' },
      unread: 1,
    },
    {
      id: 'friend',
      name: '엄친아 모임',
      desc: '친구 그룹 · 약 정보 공유',
      memberCount: 6,
      avatars: [
        { name: '한', tint: '#FA73E3' },
        { name: '정', tint: '#26C76C' },
        { name: '강', tint: '#0066FF' },
      ],
      lastEvent: { who: '정수민', text: '메모: 약사가 추천한 영양제…', time: '3일 전', kind: 'note' },
      unread: 0,
    },
  ];

  const eventColor = {
    done: { bg: 'var(--c-green-95)', fg: 'var(--c-green-40)', dot: 'var(--status-positive)' },
    miss: { bg: 'var(--c-red-95)',   fg: 'var(--c-red-40)',   dot: 'var(--status-negative)' },
    ai:   { bg: 'var(--c-violet-95)', fg: 'var(--c-violet-45)', dot: 'var(--c-violet-45)' },
    note: { bg: 'var(--c-yellow-95, #FEF4A8)', fg: '#8a6f2a', dot: '#D7B33A' },
  };

  return (
    <div style={{ ...screenStyle, background: C.bg }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="그룹"
        sub="5개 · 안 읽음 3"
        right={
          <div style={{ display: 'flex', gap: 6 }}>
            <Icon name="search" size={22} stroke={1.8} />
          </div>
        }
      />

      {/* segmented filter */}
      <div style={{ ...padX, paddingTop: 4, paddingBottom: 14, display: 'flex', gap: 6 }}>
        {['전체', '내가 환자', '내가 보호자', '비공개'].map((t, i) => {
          const on = i === 0;
          return (
            <div key={t} style={{
              padding: '7px 12px', borderRadius: 9999,
              background: on ? C.text : C.fill,
              color: on ? '#fff' : C.muted,
              fontSize: 12, fontWeight: on ? 700 : 500,
            }}>{t}</div>
          );
        })}
      </div>

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 100 }}>
        {/* Pinned section */}
        <div style={{ ...padX, paddingBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
          <Icon name="pin" size={12} stroke={2} />
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>고정됨</div>
        </div>

        <div style={padX}>
          {groups.filter(g => g.pinned).map(g => <GroupRow key={g.id} g={g} eventColor={eventColor} />)}
        </div>

        {/* All groups */}
        <div style={{ ...padX, paddingTop: 16, paddingBottom: 6 }}>
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>모든 그룹 · 4</div>
        </div>

        <div style={padX}>
          {groups.filter(g => !g.pinned).map(g => <GroupRow key={g.id} g={g} eventColor={eventColor} />)}
        </div>

        {/* Empty state cue / create more */}
        <div style={{ ...padX, paddingTop: 16 }}>
          <div style={{
            background: 'var(--c-blue-99)', borderRadius: 14, padding: 16,
            border: `1px dashed var(--c-blue-90)`,
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 40, height: 40, borderRadius: 12,
              background: '#fff', color: C.primary,
              border: `1px solid var(--c-blue-90)`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name="plus" size={22} stroke={2.4} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>
                새 그룹 만들기
              </div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 2, lineHeight: '17px' }}>
                가족·친구·요양 시설 등 새로운 그룹을 만들어요
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* FAB */}
      <div style={{
        position: 'absolute', right: 20, bottom: 96, zIndex: 5,
        width: 56, height: 56, borderRadius: 9999,
        background: C.primary, color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 10px 22px rgba(0,102,255,0.36)',
      }}>
        <Icon name="plus" size={26} stroke={2.4} />
      </div>

      <TabBar active="group" />
    </div>
  );
}

function GroupRow({ g, eventColor }) {
  const ev = eventColor[g.lastEvent.kind] || eventColor.note;
  return (
    <div style={{
      background: '#fff', borderRadius: 14, padding: '14px 14px',
      border: `1px solid ${C.line}`,
      display: 'flex', gap: 12, alignItems: 'flex-start',
      marginBottom: 8,
      boxShadow: g.unread > 0 ? '0 4px 12px rgba(0,102,255,0.06)' : 'none',
      borderColor: g.unread > 0 ? 'var(--c-blue-90)' : C.line,
    }}>
      {/* Stacked avatars */}
      <div style={{ position: 'relative', width: 52, height: 44, flexShrink: 0 }}>
        {g.personal ? (
          <Avatar name={g.avatars[0].name} tint={g.avatars[0].tint} size={44} />
        ) : (
          <>
            {g.avatars.slice(0, 3).map((a, i) => (
              <div key={i} style={{
                position: 'absolute', left: i * 14, top: 0,
                borderRadius: '50%', border: '2px solid #fff',
              }}>
                <Avatar name={a.name} tint={a.tint} size={28} />
              </div>
            ))}
          </>
        )}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        {/* row 1: name + time */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.012em' }}>
            {g.name}
          </div>
          {g.personal && (
            <div style={{
              padding: '1px 6px', borderRadius: 4,
              background: C.fill, color: C.alt,
              fontSize: 10, fontWeight: 700,
            }}>비공개</div>
          )}
          <div style={{ fontSize: 12, color: C.alt, fontWeight: 500, marginLeft: 'auto' }}>{g.lastEvent.time}</div>
        </div>
        {/* row 2: members */}
        <div style={{ fontSize: 12, color: C.alt, marginTop: 2 }}>
          {g.memberCount}명 · {g.desc}
        </div>
        {/* row 3: last event */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 8 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            padding: '3px 8px', borderRadius: 9999,
            background: ev.bg, color: ev.fg,
            fontSize: 11, fontWeight: 600, flexShrink: 0,
          }}>
            <div style={{ width: 4, height: 4, borderRadius: '50%', background: ev.dot }} />
            {g.lastEvent.who}
          </div>
          <div style={{
            flex: 1, fontSize: 13, color: C.muted, fontWeight: 500,
            overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis',
          }}>
            {g.lastEvent.text}
          </div>
          {g.unread > 0 && (
            <div style={{
              minWidth: 18, height: 18, padding: '0 6px',
              borderRadius: 9999,
              background: C.primary, color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 11, fontWeight: 700,
            }}>{g.unread}</div>
          )}
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenGroups });
